import * as vscode from 'vscode';
import { pipeProcessOutToVSCodeOutput, removeFromStateOnceExited, spawnFuzzerProcess } from '../fuzzer/process';
import { IProcessState } from '../types/state';
import { IExtensionContext } from '../types/context';
import { getCurrentSelection, writeToTempFile } from '../utils';

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

        processState.process = spawnFuzzerProcess(ctx.context.extensionPath, currentSelectionTmpFile, false);
        removeFromStateOnceExited(processState, ctx.state);
        pipeProcessOutToVSCodeOutput(processState, ctx.output);
    } catch (error) {
        vscode.window.showErrorMessage(`Error running fuzzer on selection: ${(error as Error).message}`);
    }
};
