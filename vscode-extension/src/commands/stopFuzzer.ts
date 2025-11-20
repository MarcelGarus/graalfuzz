import * as vscode from 'vscode';

import { IState } from '../types/state';
import { IExtensionContext } from '../types/context';

export default (state: IState, ctx: IExtensionContext) => async () => {
    if (state.process) {
        state.process!.kill();
        state.process = null;
        vscode.window.showInformationMessage('Fuzzer process terminated.');
    } else {
        vscode.window.showInformationMessage('No active fuzzer process.');
    }
};