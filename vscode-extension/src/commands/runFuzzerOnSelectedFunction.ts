import * as vscode from 'vscode';

import { pipeProcessOutToVSCodeOutput, removeFromStateOnceExited, spawnFuzzerProcess } from '../fuzzer/process';
import { IExtensionContext, IProcessState } from '../types/state';

export default (ctx: IExtensionContext) => async () => {
    try {
        if (ctx.state.processes.length > 0) {
            vscode.window.showWarningMessage('Fuzzer already running. Stop it first.');
            return;
        }

        const editor = vscode.window.activeTextEditor;
        if (!editor) {
            vscode.window.showErrorMessage('No active editor. Open a file to select a function.');
            return;
        }

        // Get all symbols from the current file
        const symbols = await vscode.commands.executeCommand<vscode.DocumentSymbol[]>(
            'vscode.executeDocumentSymbolProvider',
            editor.document.uri
        );

        // Filter to functions only
        const functions = symbols.filter(s =>
            s.kind === vscode.SymbolKind.Function ||
            s.kind === vscode.SymbolKind.Method
        );

        if (functions.length === 0) {
            vscode.window.showErrorMessage('No functions found in file');
            return;
        }

        // Show quick pick for function selection
        const selected = await vscode.window.showQuickPick(
            functions.map(f => ({
                label: `$(symbol-function) ${f.name}`,
                description: `Line ${f.range.start.line + 1}`,
                detail: f.detail,
                functionName: f.name,
                range: f.range
            })),
            {
                placeHolder: 'Select a function to fuzz',
                matchOnDescription: true,
                matchOnDetail: true
            }
        );

        if (!selected) {
            return; // User cancelled
        }

        ctx.output.show(true);
        ctx.output.appendLine(`Starting graalfuzz on function '${selected.functionName}'...`);

        const file = editor.document.fileName;

        // TODO: Pass function name to CLI when it supports function-specific fuzzing
        // For now, just fuzz the entire file
        const process = spawnFuzzerProcess(ctx.context.extensionPath, file);
        const processState: IProcessState = { process };
        ctx.state.processes.push(processState);
        removeFromStateOnceExited(processState, ctx.state);
        pipeProcessOutToVSCodeOutput(processState, ctx.output);

    } catch (error) {
        vscode.window.showErrorMessage(`Error running fuzzer on selected function: ${(error as Error).message}`);
    }
};
