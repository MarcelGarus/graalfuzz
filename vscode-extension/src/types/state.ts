import { ChildProcessWithoutNullStreams } from "child_process";

import * as vscode from "vscode";

export interface IState {
    process: ChildProcessWithoutNullStreams | null;
}

export const createState = ({
    process = null,
} = {}): IState => ({
    process,
});
