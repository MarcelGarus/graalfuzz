import * as vscode from 'vscode';
import { FuzzLensContext } from '../types/context';
import { getCache } from '../services/cache';
import { ResultGroup, GroupKey, SingleKey, CompositeKey, KeyPart, Shape } from '../types/state';
import { formatShape, formatValue, formatPropertyShapeUnion, formatShapeNew, formatGroupKey, formatKeyPart, formatKeyValue } from './formatting';
import { getFunctionAtPosition } from '../services/symbols';
import { getLanguageSelector } from '../config/languages';

/**
 * Provides hover information for function parameters and return types.
 * Uses cached fuzzing results to show observed types.
 */
export class FuzzLensHoverProvider implements vscode.HoverProvider {
    constructor(private readonly ctx: FuzzLensContext) { }

    async provideHover(
        document: vscode.TextDocument,
        position: vscode.Position,
        token: vscode.CancellationToken
    ): Promise<vscode.Hover | null> {
        const filePath = document.uri.fsPath;
        const cache = getCache();

        // First, check if we're inside any function (using LSP)
        const containingFunction = await getFunctionAtPosition(document, position);
        if (!containingFunction) {
            return null;
        }

        const wordRange = document.getWordRangeAtPosition(position);
        if (!wordRange) {
            return null;
        }

        // Check if we're hovering over the function name itself
        const isOnFunctionName = containingFunction.selectionRange.contains(position);

        const cachedResult = cache.get(filePath, containingFunction.name);
        if (!cachedResult) {
            if (isOnFunctionName) {
                return new vscode.Hover(
                    new vscode.MarkdownString(
                        `**FuzzLens**: Function \`${containingFunction.name}\` found but not yet fuzzed.\n\n` +
                        `Run "FuzzLens: Fuzz Current File" to analyze.`
                    ),
                    wordRange
                );
            }
            return null;
        }

        if (isOnFunctionName) {
            try {
                const markdown = this.buildFunctionHoverContent(containingFunction.name, cachedResult.analyses);
                return new vscode.Hover(markdown, wordRange);
            } catch (error) {
                console.error('Error building function hover content:', error);
                return new vscode.Hover(
                    new vscode.MarkdownString(
                        `**FuzzLens**: Error retrieving data for function \`${containingFunction.name}\`: ${(error as Error).message} \n\n` +
                        `Please try fuzzing again.`
                    ),
                    wordRange
                );
            }
        }

        // We're inside the function but not on the function name
        // Check if we're on a parameter
        const word = document.getText(wordRange);
        
        // NOTE: SignatureHelpProvider limitation:
        // vscode.executeSignatureHelpProvider typically returns undefined when called
        // outside a function call context (i.e., not at the opening parenthesis).
        // To make this work, we would need to:
        // 1. Parse the function signature ourselves using the document symbols
        // 2. Match the word at the cursor position against parameter names
        // 3. Determine which parameter index the word represents
        // For now, parameter hover is not implemented - only function name hover works.
        // Try to get parameter info from signature help
        const sigHelp = await vscode.commands.executeCommand<vscode.SignatureHelp>(
            'vscode.executeSignatureHelpProvider',
            document.uri,
            position
        );

        if (sigHelp?.signatures?.length) {
            const sig = sigHelp.signatures[sigHelp.activeSignature ?? 0];
            const paramIndex = sig.parameters?.findIndex(p => {
                const label = typeof p.label === 'string' ? p.label : p.label[0];
                return label === word;
            }) ?? -1;

            if (paramIndex >= 0 && sig.parameters) {
                const paramLabel = typeof sig.parameters[paramIndex].label === 'string' 
                    ? sig.parameters[paramIndex].label 
                    : sig.parameters[paramIndex].label[0];
                
                const md = this.buildParameterHoverContent(
                    containingFunction.name,
                    paramLabel.toString(),
                    paramIndex,
                    cachedResult.analyses
                );
                return new vscode.Hover(md, wordRange);
            }
        }

        return null;
    }

    private buildParameterHoverContent(
        functionName: string,
        paramName: string,
        paramIndex: number,
        analyses: Map<string, ResultGroup>
    ): vscode.MarkdownString {
        const md = new vscode.MarkdownString();
        md.isTrusted = true;
        md.supportHtml = true;

        md.appendMarkdown(`### 🔧 FuzzLens Parameter: \`${paramName}\`\n\n`);
        md.appendMarkdown(`Function: \`${functionName}\`\n\n`);

        // Show observed input shapes for this parameter
        const typeTable = analyses.get('inputShapeOutputTypeTable');
        if (typeTable && typeTable.children && typeTable.children?.length > 0) {
            const inputShapes = new Set<string>();
            
            for (const group of typeTable.children) {
                // Extract input shape from the group key
                if (group.key.type === 'Composite') {
                    const compositeKey = group.key as CompositeKey;
                    for (const part of compositeKey.parts) {
                        if (part.column === 'InputShape' || part.column === 'inputShape') {
                            // value is a Shape object
                            if (typeof part.value === 'object' && part.value && 'type' in part.value) {
                                inputShapes.add(formatShapeNew(part.value as Shape));
                            }
                        }
                    }
                } else if (group.key.type === 'Single') {
                    const singleKey = group.key as SingleKey;
                    if (singleKey.column === 'InputShape' || singleKey.column === 'inputShape') {
                        if (typeof singleKey.value === 'object' && singleKey.value && 'type' in singleKey.value) {
                            inputShapes.add(formatShapeNew(singleKey.value as Shape));
                        }
                    }
                }
            }

            if (inputShapes.size > 0) {
                md.appendMarkdown(`**Observed Input Shapes:**\n\n`);
                const shapesArray = Array.from(inputShapes);
                for (const shape of shapesArray.slice(0, 10)) {
                    md.appendMarkdown(`- \`${shape}\`\n`);
                }
                if (shapesArray.length > 10) {
                    md.appendMarkdown(`\n*+${shapesArray.length - 10} more shapes...*\n`);
                }
            } else {
                md.appendMarkdown(`*No input shape data available yet. Run fuzzer to collect data.*\n`);
            }
        } else {
            md.appendMarkdown(`*No fuzzing data available. Run fuzzer to collect parameter information.*\n`);
        }

        return md;
    }

    private buildFunctionHoverContent(
        functionName: string,
        analyses: Map<string, ResultGroup>
    ): vscode.MarkdownString {
        const md = new vscode.MarkdownString();
        md.isTrusted = true;
        md.supportHtml = true;

        md.appendMarkdown(`### 🔍 FuzzLens: \`${functionName}\`\n\n`);

        // Get signature from observedSignature query
        const signature = analyses.get('observedSignature');
        if (signature) {
            // InputShapes is a property shape union: Map<String, Set<Shape>>
            const inputShapes = signature.aggregations?.['InputShapes'] as Record<string, unknown> | undefined;
            const outputTypes = signature.aggregations?.['OutputTypes'] as Set<string> | string[] | undefined;
            
            md.appendMarkdown(`**Signature:** `);
            
            // Format input shapes using the property shape union formatter
            if (inputShapes && Object.keys(inputShapes).length > 0) {
                const inputStr = formatPropertyShapeUnion(inputShapes);
                md.appendMarkdown(`\`${inputStr}\``);
            } else {
                md.appendMarkdown(`\`any\``);
            }
            
            md.appendMarkdown(` → `);
            
            // Format output types
            if (outputTypes) {
                const outputArr = Array.isArray(outputTypes) ? outputTypes : [...outputTypes];
                if (outputArr.length > 0) {
                    const outputStr = outputArr.slice(0, 5).join(' | ');
                    md.appendMarkdown(`\`${outputStr}\``);
                    if (outputArr.length > 5) {
                        md.appendMarkdown(` +${outputArr.length - 5} more`);
                    }
                } else {
                    md.appendMarkdown(`\`unknown\``);
                }
            } else {
                md.appendMarkdown(`\`unknown\``);
            }
            
            md.appendMarkdown(`\n\n`);
        } else {
            md.appendMarkdown(`*No signature data available.*\n\n`);
        }

        const typeTable = analyses.get('inputShapeOutputTypeTable');
        if (typeTable && typeTable.children && typeTable.children.length > 0) {
            md.appendMarkdown(`**Type Mapping:**\n\n`);
            for (const group of typeTable.children) {
                const keyStr = formatGroupKey(group.key);
                const count = group.aggregations?.['Count'] as number | undefined;
                const inputShapes = group.aggregations?.['InputShapes'];
                const countStr = count ? ` (×${count})` : '';

                if (inputShapes) {
                    // inputShapes is a property shape union, format it
                    const inputStr = typeof inputShapes === 'object'
                        ? formatPropertyShapeUnion(inputShapes as Record<string, unknown>)
                        : String(inputShapes);
                    md.appendMarkdown(`- \`${inputStr}\` → \`${keyStr}\`${countStr}\n`);
                } else {
                    md.appendMarkdown(`- \`${keyStr}\`${countStr}\n`);
                }
            }
        } else {
            md.appendMarkdown(`*No type mapping data available.*\n\n`);
        }
        md.appendMarkdown(`\n`);

        const exceptions = analyses.get('exceptionExamples');
        if (exceptions && exceptions.children && exceptions.children.length > 0) {
            md.appendMarkdown(`**Exceptions:**\n\n`);
            for (const group of exceptions.children.slice(0, 3)) {
                const exceptionType = formatGroupKey(group.key);
                const count = group.aggregations?.['Count'] as number | undefined;
                const countStr = count ? ` (×${count})` : '';

                let exampleStr = '';
                if (group.samples && group.samples.length > 0) {
                    const example = group.samples[0];
                    const inputStr = formatValue(example.input, example.universe);
                    exampleStr = ` e.g. \`${inputStr}\``;
                }

                md.appendMarkdown(`- ⚠️ ${exceptionType}${countStr}${exampleStr}\n`);
            }
            md.appendMarkdown(`\n`);
        } else {
            md.appendMarkdown(`*No exceptions observed so far.*\n\n`);
        }

        const validExamples = analyses.get('validExamples');
        if (validExamples && validExamples.samples && validExamples.samples.length > 0) {
            md.appendMarkdown(`**Examples:**\n\n`);
            let exampleCount = 0;
            for (const result of validExamples.samples.slice(0, 1)) {
                const input = formatValue(result.input, result.universe);
                md.appendMarkdown(`- \`${input}\` → \`${result.value}\`\n`);
                exampleCount++;
                if (exampleCount >= 4) { break; }
            }
        } else {
            md.appendMarkdown(`*No valid examples observed so far.*\n\n`);
        }

        return md;
    }
}

export function registerHoverProvider(ctx: FuzzLensContext): vscode.Disposable {
    const provider = new FuzzLensHoverProvider(ctx);
    const selector = getLanguageSelector();

    return vscode.languages.registerHoverProvider(selector, provider);
}
