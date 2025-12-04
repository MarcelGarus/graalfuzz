import * as vscode from "vscode";

import { ChildProcessWithoutNullStreams } from "child_process";

export interface IState {
    // TODO: Store map from (file, function) to process state. Only allow one process per (file, function) pair. Cancel existing process if a new one is started for the same (file, function).
    processes: IProcessState[];

    // TODO: Store a map from (file, function) to decorated results to avoid re-decorating the same results multiple times. When a file is saved, invalidate decorations of that file. Can we estimate dependents of saved files to also invalidate them?
}

export interface IProcessState {
    process?: ChildProcessWithoutNullStreams;
    tmpFile?: string;
    stdout?: Promise<string>;
    stderr?: Promise<string>;
    results?: Promise<IFuzzerResult[]>;
}

export interface IFuzzerResult {
    universe: IUniverse;
    input: IValue;
    trace: ITrace;
}

export interface IUniverse {
    objects: Record<string, IObjectDefinition>;
}

export interface IObjectDefinition {
    members: Record<string, IValue>;
}

export interface ITrace {
    entries: ITraceEntry[];
}

export type ITraceEntry =
    | { type: "Call"; arg: Partial<Omit<IValue, "type">> }
    | { type: "QueryMember"; id: { value: number }; key: string }
    | { type: "Member"; id: { value: number }; key: string }
    | { type: "Return"; typeName: string; value: string }
    | { type: "Crash"; message: string };

export type IValue =
    | { type: "Null" }
    | { type: "Boolean"; value: boolean }
    | { type: "Int"; value: number }
    | { type: "Double"; value: number }
    | { type: "String"; value: string }
    | { type: "Object"; id: { value: number } };

export const createState = ({
    processes = [],
} = {}): IState => ({
    processes,
});
