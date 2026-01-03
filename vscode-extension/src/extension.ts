// The module 'vscode' contains the VS Code extensibility API
// Import the module and reference it with the alias vscode in your code below
import * as vscode from 'vscode';

import { createState, IState } from './types/state';

import runFuzzerOnCurrentFile from './commands/runFuzzerOnCurrentFile';
import stopFuzzer from './commands/stopFuzzer';
import runFuzzerOnSelection from './commands/runFuzzerOnSelection';
import { cleanup } from './fuzzer/process';
import { createContext, IExtensionContext } from './types/context';
import { setupFuzzerResultsListener } from './fuzzer/displayResults';

// Store for access in deactivate
let extensionContext: IExtensionContext;

// This method is called when your extension is activated
// Your extension is activated the very first time the command is executed
export function activate(context: vscode.ExtensionContext) {
	// Use the console to output diagnostic information (console.log) and errors (console.error)
	// This line of code will only be executed once when your extension is activated
	console.log('Extension "graalfuzz" active. Using CLI backend.');

	extensionContext = createContext({ context });

	const runFuzzerOnCurrentFileCmd = vscode.commands.registerCommand('graalfuzz.runFuzzerOnCurrentFile', runFuzzerOnCurrentFile(extensionContext));
	const runFuzzerOnSelectionCmd = vscode.commands.registerCommand('graalfuzz.runFuzzerOnSelection', runFuzzerOnSelection(extensionContext));
	const stopFuzzerCmd = vscode.commands.registerCommand('graalfuzz.stopFuzzer', stopFuzzer(extensionContext));
	const fuzzerResultsListener = setupFuzzerResultsListener(extensionContext);
	
	context.subscriptions.push(
		runFuzzerOnCurrentFileCmd,
		runFuzzerOnSelectionCmd,
		stopFuzzerCmd,
		extensionContext.output,
		fuzzerResultsListener
	);
}

// This method is called when your extension is deactivated
export function deactivate() {
	cleanup(extensionContext.state);
}
