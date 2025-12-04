import { ChildProcessWithoutNullStreams, spawn } from 'child_process';
import * as path from 'path';
import * as vscode from 'vscode';
import { IProcessState, IState } from '../types/state';
import * as fs from 'fs';
import { IExtensionContext } from '../types/context';

export const spawnFuzzerProcess = (extensionPath: string, file: string, toJSON: boolean = true): ChildProcessWithoutNullStreams => {
    // TODO: Move the platform specific native builds into the extension directory. Part of distribution when published.
    // Choose script based on platform
    const isWin = process.platform === 'win32';
    const script = isWin
        ? path.join(extensionPath, '..', 'graalfuzz.cmd')
        : path.join(extensionPath, '..', 'graalfuzz.sh');
    const args = ['--file', `"${file}"`, '--no-color'];
    if (toJSON) {
        args.push('--tooling');
    }
    console.log(`Spawning fuzzer process: ${script} ${args.join(' ')}`);
    return spawn(script, args, { stdio: 'pipe', shell: isWin, cwd: path.join(extensionPath, '..') });
};

export const pipeProcessOutToVSCodeOutput = (processState: IProcessState, outputChannel: vscode.OutputChannel) => {
    const process = processState.process;
    if (!process) {
        throw new Error('No process found in process state.');
    }

    process.stdout.setEncoding('utf8');
    process.stdout.on('data', (d: string) => outputChannel.append(d));

    process.stderr.setEncoding('utf8');
    process.stderr.on('data', (d: string) => outputChannel.append('[stderr] ' + d));

    process.on('exit', code => {
        outputChannel.appendLine('\nFuzzer exited with code ' + code);
    });
};

export const writeProcessOutputToState = (ctx: IExtensionContext, processState: IProcessState) => {
    const process = processState.process;
    if (!process) {
        throw new Error('No process found in process state.');
    }

    let stdout = '';
    // once the process exits, we resolve the promises
    processState.stdout = new Promise<string>((resolve) => {
        process.on('exit', () => {
            resolve(stdout);
        });
    });
    process.stdout.setEncoding('utf8');
    process.stdout.on('data', (d: string) => {
        stdout += d;
    });
    // TODO: We could also stream results line by line without having to wait for process exit
    createFuzzerResultsOnceExited(ctx, processState);

    let stderr = '';
    processState.stderr = new Promise<string>((resolve) => {
        process.on('exit', () => {
            resolve(stderr);
        });
    });
    process.stderr.setEncoding('utf8');
    process.stderr.on('data', (d: string) => {
        stderr += '[stderr] ' + d;
    });
};

const createFuzzerResultsOnceExited = async (ctx: IExtensionContext, processState: IProcessState) => {
    if (!processState.stdout) {
        vscode.window.showErrorMessage('No stdout promise found in process state.');
        return;
    }

    try {
        const data = await processState.stdout;
        const lines = data.trim().split('\n').filter(line => line.trim());
        const results = lines.map(line => JSON.parse(line));

        processState.results = Promise.resolve(results);
        ctx.events.onFuzzerResultsReady.fire(processState);
    } catch (err) {
        vscode.window.showErrorMessage(`Error parsing fuzzer output: ${(err as Error).message}`);
        return;
    }
};

export const removeFromStateOnceExited = (processState: IProcessState, state: IState) => {
    if (!processState.process) {
        throw new Error('No process found in process state.');
    }
    processState.process.on('exit', () => {
        state.processes = state.processes.filter(p => p.process?.pid !== processState.process!.pid);
    });
};

export const cleanup = (state: IState) => {
    killFuzzerProcesses(state.processes);
    deleteTmpFiles(state.processes);
    state.processes = [];
};

export const killFuzzerProcesses = (processes: IProcessState[]) => {
    processes.forEach(proc => {
        try {
            proc.process?.kill?.();
        } catch (e) {
            vscode.window.showErrorMessage(`Error killing fuzzer process (PID: ${proc.process?.pid}): ${(e as Error).message}`);
        }
    });
};

export const deleteTmpFiles = (processes: IProcessState[]) => {
    processes.forEach(proc => {
        if (proc.tmpFile) {
            fs.unlink(proc.tmpFile, (err: NodeJS.ErrnoException | null) => {
                if (err) {
                    vscode.window.showErrorMessage(`Error deleting temp file ${proc.tmpFile}: ${err.message}`);
                }
            });
        }
    });
};
