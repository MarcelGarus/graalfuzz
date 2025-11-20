import * as vscode from 'vscode';
import * as path from 'path';
import { ChildProcess, ChildProcessWithoutNullStreams, spawn } from 'child_process';

import { IState } from '../types/state';
import { IExtensionContext } from '../types/context';

export default (state: IState, ctx: IExtensionContext) => async () => {
    if (state.process) {
        vscode.window.showWarningMessage('Fuzzer already running. Stop it first.');
        return;
    }
    ctx.output.show(true);
    ctx.output.appendLine('Starting graalfuzz...');
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

    state.process = spawnFuzzerProcess(ctx.context.extensionPath, file);

    state.process!.stdout.setEncoding('utf8');
    state.process!.stdout.on('data', (d: string) => ctx.output.append(d));
    state.process!.stderr.setEncoding('utf8');
    state.process!.stderr.on('data', (d: string) => ctx.output.append('[stderr] ' + d));
    state.process.on('exit', code => {
        ctx.output.appendLine('\nFuzzer exited with code ' + code);
        state.process = null;
    });
};

const spawnFuzzerProcess = (extensionPath: string, file: string): ChildProcessWithoutNullStreams => {
    const isWin = process.platform === 'win32';
    const script = isWin
        ? path.join(extensionPath, '..', 'graalfuzz.cmd')
        : path.join(extensionPath, '..', 'graalfuzz.sh');
    const args = ['--file', `"${file}"`];
    console.log(`Spawning fuzzer process: ${script} ${args.join(' ')}`);
    return spawn(script, args, { stdio: 'pipe', shell: isWin, cwd: path.join(extensionPath, '..') });
};