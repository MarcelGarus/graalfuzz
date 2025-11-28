import * as vscode from 'vscode';

import { removeFromStateOnceExited, spawnFuzzerProcess, writeProcessOutputToState } from '../fuzzer/process';
import { IProcessState } from '../types/state';
import { IExtensionContext } from '../types/context';

export default (ctx: IExtensionContext) => async () => {
    try {
        if (ctx.state.processes.length > 0) {
            vscode.window.showWarningMessage('Fuzzer already running. Stop it first.');
            return;
        }
        // Get the content of the current active editor
        const editor = vscode.window.activeTextEditor;
        if (!editor) {
            vscode.window.showErrorMessage('No active editor. Open a file to pass its content to the fuzzer.');
            return;
        }
        const file = editor.document.fileName;
        console.log(file);

        ctx.output.show(true);
        ctx.output.appendLine('Starting graalfuzz...');

        const process = spawnFuzzerProcess(ctx.context.extensionPath, file);
        const processState: IProcessState = { process };
        ctx.state.processes.push(processState);
        removeFromStateOnceExited(processState, ctx.state);
        writeProcessOutputToState(ctx, processState); // -> onFuzzerResultsReady event
    } catch (error) {
        vscode.window.showErrorMessage(`Error running fuzzer on current file: ${(error as Error).message}`);
    }
};
