import * as vscode from 'vscode';
import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';

export const applyDecoration = (editor: vscode.TextEditor, line: number, suggestion: string) => {
  const substring = trunctateSuggestion(suggestion);
  const decorationType = vscode.window.createTextEditorDecorationType({
    after: {
      contentText: ` ${substring}`,
      color: 'grey'
    }
  });

  const lineLength = editor.document.lineAt(line - 1).text.length;
  const range = new vscode.Range(
    new vscode.Position(line - 1, lineLength),
    new vscode.Position(line - 1, lineLength)
  );

  const decoration = { range: range, hoverMessage: suggestion };

  editor?.setDecorations(decorationType, [decoration]);
};

const trunctateSuggestion = (suggestion: string, maxLength: number = 75): string => {
  let substring = suggestion;
  if (substring.length > maxLength) {
    if (substring.endsWith('}') || substring.endsWith(']')) {
      substring = substring.substring(0, 21) + '...' + substring.substring(substring.length - 1);
    } else {
      substring = substring.substring(0, 22) + '...';
    }
  }
  return substring;
};

export const getCurrentSelection = (): string | null => {
    const editor = vscode.window.activeTextEditor;
    if (editor) {
        const selection = editor.selection;
        if (selection && !selection.isEmpty) {
            const selectionRange = new vscode.Range(selection.start.line, selection.start.character, selection.end.line, selection.end.character);
            const highlighted = editor.document.getText(selectionRange);
            return highlighted;
        }
    }
    return null;
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
