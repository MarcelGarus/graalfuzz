import * as vscode from 'vscode';

import { cleanup } from '../fuzzer/process';
import { IExtensionContext } from '../types/context';

export default (ctx: IExtensionContext) => async () => {
    try {
        if (ctx.state.processes) {
            cleanup(ctx.state);
            vscode.window.showInformationMessage('Fuzzer process terminated.');
        } else {
            vscode.window.showInformationMessage('No active fuzzer process.');
        }
    } catch (error) {
        vscode.window.showErrorMessage(`Error while stopping fuzzer: ${(error as Error).message}`);
    }
};
