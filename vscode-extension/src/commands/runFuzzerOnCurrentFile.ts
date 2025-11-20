import * as vscode from 'vscode';

import { pipeProcessOutToVSCodeOutput, removeFromStateOnceExited, spawnFuzzerProcess } from '../fuzzer/process';
import { IExtensionContext, IProcessState } from '../types/state';

export default (ctx: IExtensionContext) => async () => {
    try {
        if (ctx.state.processes.length > 0) {
            vscode.window.showWarningMessage('Fuzzer already running. Stop it first.');
            return;
        }
        ctx.output.show(true);
        ctx.output.appendLine('Starting graalfuzz...');
        // Get the content of the current active editor
        const editor = vscode.window.activeTextEditor;
        if (!editor) {
            vscode.window.showErrorMessage('No active editor. Open a file to pass its content to the fuzzer.');
            return;
        }
        const file = editor.document.fileName;
        console.log(file);
    
        const process = spawnFuzzerProcess(ctx.context.extensionPath, file);
        const processState: IProcessState = { process };
        ctx.state.processes.push(processState);
        removeFromStateOnceExited(processState, ctx.state);
        pipeProcessOutToVSCodeOutput(processState, ctx.output);
    } catch (error) {
        vscode.window.showErrorMessage(`Error running fuzzer on current file: ${(error as Error).message}`);
    }
};
