// The module 'vscode' contains the VS Code extensibility API
// Import the module and reference it with the alias vscode in your code below
import * as vscode from 'vscode';
import { spawn, ChildProcessWithoutNullStreams } from 'child_process';
import * as path from 'path';

// Fuzzer run (long-lived streaming output)
let fuzzProcess: ChildProcessWithoutNullStreams | undefined;

// This method is called when your extension is activated
// Your extension is activated the very first time the command is executed
export function activate(context: vscode.ExtensionContext) {

	// Use the console to output diagnostic information (console.log) and errors (console.error)
	// This line of code will only be executed once when your extension is activated
	console.log('Extension "graalfuzz" active. Using CLI backend.');

	// The command has been defined in the package.json file
	// Now provide the implementation of the command with registerCommand
	// The commandId parameter must match the command field in package.json
	const disposable = vscode.commands.registerCommand('graalfuzz.helloWorld', () => {
		// The code you place here will be executed every time your command is executed
		// Display a message box to the user
		vscode.window.showInformationMessage('Hello World from graalfuzz!');
	});

	const output = vscode.window.createOutputChannel('GraalFuzz Fuzzer');

	const runFuzzer = vscode.commands.registerCommand('graalfuzz.runFuzzer', async () => {
		if (fuzzProcess) {
			vscode.window.showWarningMessage('Fuzzer already running. Stop it first.');
			return;
		}
		output.show(true);
		output.appendLine('Starting graalfuzz...');
		// Get the content of the current active editor
		const editor = vscode.window.activeTextEditor;
		if (!editor) {
			vscode.window.showErrorMessage('No active editor. Open a file to pass its content to the fuzzer.');
			return;
		}
		const file = editor.document.fileName;

		console.log(file);

		// TODO: Move the platform specific native builds into the extension directory. Part of distribution when published.
		// Choose script based on platform
		const isWin = process.platform === 'win32';
		const script = isWin
			? path.join(context.extensionPath, '..', 'graalfuzz.cmd')
			: path.join(context.extensionPath, '..', 'graalfuzz.sh');
		const args = ['--file', `"${file}"`];
		console.log(`Spawning fuzzer process: ${script} ${args.join(' ')}`);
		fuzzProcess = spawn(script, args, { stdio: 'pipe', shell: isWin, cwd: path.join(context.extensionPath, '..') });
		fuzzProcess.stdout.setEncoding('utf8');
		fuzzProcess.stdout.on('data', (d: string) => output.append(d));
		fuzzProcess.stderr.setEncoding('utf8');
		fuzzProcess.stderr.on('data', (d: string) => output.append('[stderr] ' + d));
		fuzzProcess.on('exit', code => {
			output.appendLine('\nFuzzer exited with code ' + code);
			fuzzProcess = undefined;
		});
	});

	const stopFuzzer = vscode.commands.registerCommand('graalfuzz.stopFuzzer', async () => {
		if (!fuzzProcess) {
			vscode.window.showInformationMessage('No active fuzzer process.');
			return;
		}
		fuzzProcess.kill();
		fuzzProcess = undefined;
		vscode.window.showInformationMessage('Fuzzer process terminated.');
	});

	context.subscriptions.push(disposable, runFuzzer, stopFuzzer, output);
}

// This method is called when your extension is deactivated
export function deactivate() {
	if (fuzzProcess) {
		fuzzProcess.kill();
		fuzzProcess = undefined;
	}
}
