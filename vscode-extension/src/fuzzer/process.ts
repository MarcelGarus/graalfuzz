import { ChildProcessWithoutNullStreams, spawn } from 'child_process';
import * as path from 'path';
import * as vscode from 'vscode';
import { IProcessState, IState } from '../types/state';
import * as fs from 'fs';
import * as os from 'os';

export const spawnFuzzerProcess = (extensionPath: string, file: string): ChildProcessWithoutNullStreams => {
    // TODO: Move the platform specific native builds into the extension directory. Part of distribution when published.
    // Choose script based on platform
    const isWin = process.platform === 'win32';
    const script = isWin
        ? path.join(extensionPath, '..', 'graalfuzz.cmd')
        : path.join(extensionPath, '..', 'graalfuzz.sh');
    const args = ['--file', `"${file}"`];
    console.log(`Spawning fuzzer process: ${script} ${args.join(' ')}`);
    return spawn(script, args, { stdio: 'pipe', shell: isWin, cwd: path.join(extensionPath, '..') });
};

export const writeToTempFile = async (content: string): Promise<string> => {
    const tmpDir = os.tmpdir();
    const tmpFilePath = path.join(tmpDir, `graalfuzz_${Math.random().toString(36).slice(2)}.tmp`);
    const fileHandle = await fs.promises.open(tmpFilePath, 'w', 0o600);
    try {
        await fileHandle.writeFile(content, 'utf8');
    } finally {
        await fileHandle.close();
    }
    return tmpFilePath;
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
