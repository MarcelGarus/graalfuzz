// The module 'vscode' contains the VS Code extensibility API
// Import the module and reference it with the alias vscode in your code below
import * as vscode from 'vscode';

import { createState, IState } from './types/state';
import { IExtensionContext } from './types/context';

import runFuzzerOnCurrentFile from './commands/runFuzzerOnCurrentFile';
import stopFuzzer from './commands/stopFuzzer';

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
	};

	const runFuzzerCmd = vscode.commands.registerCommand('graalfuzz.runFuzzerOnCurrentFile', runFuzzerOnCurrentFile(state, extensionContext));
	const stopFuzzerCmd = vscode.commands.registerCommand('graalfuzz.stopFuzzer', stopFuzzer(state, extensionContext));

	context.subscriptions.push(
		runFuzzerCmd,
		stopFuzzerCmd,
		output
	);
}

// This method is called when your extension is deactivated
export function deactivate() {
	if (state.process) {
		state.process.kill();
	}

	state = createState();
}
