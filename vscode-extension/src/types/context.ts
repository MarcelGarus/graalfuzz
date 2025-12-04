import * as vscode from "vscode";
import { createState, IProcessState, IState } from "./state";

export interface IExtensionContext {
    context: vscode.ExtensionContext;
    output: vscode.OutputChannel;
    state: IState;
    events: IEvents;
}

export interface IEvents {
    onFuzzerResultsReady: vscode.EventEmitter<IProcessState>;
}

export const createContext = ({ context }: { context: vscode.ExtensionContext }): IExtensionContext => ({
    context,
    output: vscode.window.createOutputChannel('GraalFuzz Fuzzer'),
    state: createState(),
    events: {
        onFuzzerResultsReady: new vscode.EventEmitter<IProcessState>(),
    },
});
