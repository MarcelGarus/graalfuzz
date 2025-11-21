// The module 'vscode' contains the VS Code extensibility API
// Import the module and reference it with the alias vscode in your code below
import * as vscode from 'vscode';

import { createState, IExtensionContext, IState } from './types/state';

import runFuzzerOnCurrentFile from './commands/runFuzzerOnCurrentFile';
import stopFuzzer from './commands/stopFuzzer';
import { cleanup } from './fuzzer/process';
import runFuzzerOnFunctionPicker from './commands/runFuzzerOnFunctionPicker';

// Fuzzer run (long-lived streaming output)
let state: IState;

// This method is called when your extension is activated
// Your extension is activated the very first time the command is executed
export function activate(context: vscode.ExtensionContext) {
	// Use the console to output diagnostic information (console.log) and errors (console.error)
	// This line of code will only be executed once when your extension is activated
	console.log('Extension "graalfuzz" active. Using CLI backend.');

	const output = vscode.window.createOutputChannel('GraalFuzz Fuzzer');
	state = createState();

	const extensionContext: IExtensionContext = {
		context,
		output,
		state,
	};

	const runFuzzerCmd = vscode.commands.registerCommand('graalfuzz.runFuzzerOnCurrentFile', runFuzzerOnCurrentFile(extensionContext));
	const runFuzzerOnSelectedFunctionCmd = vscode.commands.registerCommand('graalfuzz.runFuzzerOnFunction', runFuzzerOnFunctionPicker(extensionContext));
	const stopFuzzerCmd = vscode.commands.registerCommand('graalfuzz.stopFuzzer', stopFuzzer(extensionContext));
	context.subscriptions.push(
		runFuzzerCmd,
		runFuzzerOnSelectedFunctionCmd,
		stopFuzzerCmd,
		output
	);
}

// This method is called when your extension is deactivated
export function deactivate() {
	cleanup(state);

	state = createState();
}
