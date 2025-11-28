import { IFuzzerResult, IProcessState, IUniverse, IValue } from "../types/state";
import * as vscode from "vscode";
import { applyDecoration } from "../utils";
import { IExtensionContext } from "../types/context";

export const setupFuzzerResultsListener = (ctx: IExtensionContext): vscode.Disposable => {
    return ctx.events.onFuzzerResultsReady.event((processState) => {
        handleFuzzerResults(ctx, processState);
    });
};

export const handleFuzzerResults = async (ctx: IExtensionContext, processState: IProcessState) => {
    const results = await processState.results;
    if (!results || results.length === 0) {
        ctx.output.appendLine('No fuzzing results received.');
        return;
    }

    logFuzzerResults(ctx.output, results);
    addDecorationsForFuzzerResults(ctx, results);
};

const addDecorationsForFuzzerResults = (ctx: IExtensionContext, results: IFuzzerResult[]) => {
    // TODO: In state keep track of which results have been decorated for which editors (in a map in the general state). Then the event listener can listen to those changes.
    const editor = vscode.window.activeTextEditor;
    if (!editor) {
        vscode.window.showErrorMessage('No active editor to add fuzzer result decorations to.');
        return;
    }

    // TODO: Use heuristics to decide which results to use for decorations (and where to put them)
    const lastResult = results[results.length - 1];
    addDecorationForFuzzerResult(editor, lastResult);
};

const addDecorationForFuzzerResult = (editor: vscode.TextEditor, result: IFuzzerResult) => {
    // TODO: Find the right line number to add the decoration to (either from GraalVM or through pattern matching in here)
    const lineNumber = 1; // 1-indexed
    const decorationString = resultToDecorationString(result);
    applyDecoration(editor, lineNumber, decorationString);
};

const resultToDecorationString = (result: IFuzzerResult): string => {
    const inputValue = valueToString(result.input, result.universe);
    const outcome = result.trace.entries[result.trace.entries.length - 1];
    if (outcome.type === 'Crash') {
        return `${inputValue} -> Crash: ${outcome.message}`;
    } else if (outcome.type === 'Return') {
        const outputValue = valueAndTypeNameToString(outcome.value, outcome.typeName);
        return `${inputValue} -> ${outputValue}`;
    } else {
        return "";
    }
};

const logFuzzerResults = (output: vscode.OutputChannel, results: IFuzzerResult[]) => {
    output.appendLine(`Received ${results.length} fuzzing results.`);
    results.forEach(result => {
        const inputValue = valueToString(result.input, result.universe);
        output.append(`Input: ${inputValue}`);
        output.append(' -> ');
        const outcome = result.trace.entries[result.trace.entries.length - 1];
        if (outcome.type === 'Crash') {
            output.append(`Crashed: ${outcome.message}`);
        } else if (outcome.type === 'Return') {
            output.append(`${outcome.value} (${outcome.typeName})`);
        } else {
            output.append(`Unexpected last trace entry type: ${outcome.type}`);
        }
        output.appendLine('---');
    });
};

const valueAndTypeNameToString = (value: string, typeName: string): string => {
    switch (typeName) {
        case 'String':
        case 'string':
            return `"${value}"`;
        default:
            return `${value} (${typeName})`;
    };
};

const valueToString = (input: IValue, universe: IUniverse): string => {
    switch (input.type) {
        case 'Null':
            return 'Null';
        case 'Boolean':
            return input.value.toString();
        case 'Int':
            return input.value.toString();
        case 'Double':
            // show up to 4 decimal places
            let fixed = input.value.toFixed(4);
            while (fixed.endsWith('0') || fixed.endsWith('.')) {
                fixed = fixed.slice(0, -1);
            }
            return fixed;
        case 'String':
            return `"${input.value}"`;
        case 'Object':
            const objDef = universe.objects['$' + input.id.value];
            if (objDef) {
                const members = Object.entries(objDef.members);
                const memberStrings = members.map(([key, val]) => `${key}: ${valueToString(val, universe)}`);
                return `{ ${memberStrings.join(', ')} }`;
            } else {
                return 'Object (unknown)';
            }
        default:
            return 'Unknown';
    }
};
