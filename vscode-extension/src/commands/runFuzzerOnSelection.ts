import * as vscode from 'vscode';
import { pipeProcessOutToVSCodeOutput, removeFromStateOnceExited, spawnFuzzerProcess, writeToTempFile } from '../fuzzer/process';
import { IExtensionContext, IProcessState } from '../types/state';

export default (ctx: IExtensionContext) => async () => {
    try {
        if (ctx.state.processes.length > 0) {
            vscode.window.showWarningMessage('Fuzzer already running. Stop it first.');
            return;
        }

        const currentSelection = getCurrentSelection();
        if (!currentSelection) {
            vscode.window.showErrorMessage('No code selected. Please select all the code you want to fuzz.');
            return;
        }
        console.log(currentSelection);

        // TODO: Writing users code to tmp files is potentially a security risk.
        // We ensure proper file permissions and cleanup.
        // Sending the code directly via stdin would be better. However, on Windows, sending multiline input via stdin is problematic.
        const currentSelectionTmpFile = await writeToTempFile(currentSelection);
        const processState: IProcessState = {
            tmpFile: currentSelectionTmpFile,
        };
        ctx.state.processes.push(processState);

        ctx.output.show(true);
        ctx.output.appendLine('Starting graalfuzz...');

        processState.process = spawnFuzzerProcess(ctx.context.extensionPath, currentSelectionTmpFile);
        removeFromStateOnceExited(processState, ctx.state);
        pipeProcessOutToVSCodeOutput(processState, ctx.output);
    } catch (error) {
        vscode.window.showErrorMessage(`Error running fuzzer on selection: ${(error as Error).message}`);
    }
};

const getCurrentSelection = (): string | null => {
    const editor = vscode.window.activeTextEditor;
    if (editor) {
        const selection = editor.selection;
        if (selection && !selection.isEmpty) {
            const selectionRange = new vscode.Range(selection.start.line, selection.start.character, selection.end.line, selection.end.character);
            const highlighted = editor.document.getText(selectionRange);
            return highlighted;
        }
    }
    return null;
};
