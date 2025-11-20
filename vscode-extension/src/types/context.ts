import * as vscode from "vscode";

export interface IExtensionContext {
    context: vscode.ExtensionContext;
    output: vscode.OutputChannel;
}