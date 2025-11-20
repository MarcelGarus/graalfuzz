import * as vscode from "vscode";

import { ChildProcessWithoutNullStreams } from "child_process";

export interface IExtensionContext {
    context: vscode.ExtensionContext;
    output: vscode.OutputChannel;
    state: IState;
}

export interface IState {
    processes: IProcessState[];
}

export interface IProcessState {
    process?: ChildProcessWithoutNullStreams;
    tmpFile?: string;
}

export const createState = ({
    processes = [],
} = {}): IState => ({
    processes,
});
