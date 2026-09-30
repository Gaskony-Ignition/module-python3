import { useRef, useEffect } from 'react'
import { EditorView, basicSetup } from 'codemirror'
import { python } from '@codemirror/lang-python'
import { oneDark } from '@codemirror/theme-one-dark'
import { keymap } from '@codemirror/view'
import { Prec } from '@codemirror/state'
import { HighlightStyle, syntaxHighlighting } from '@codemirror/language'
import { tags } from '@lezer/highlight'

// oneDark's own comment colour measured 3.6:1 on oneDark's own editor
// background (axe color-contrast, WCAG 1.4.3) -- override just that token,
// at Prec.highest so it wins over oneDark's syntax highlighting.
const commentContrastFix = Prec.highest(syntaxHighlighting(HighlightStyle.define([
  { tag: [tags.comment, tags.lineComment, tags.blockComment], color: 'var(--text-tertiary)' },
])))

interface Props {
  value: string
  onChange: (value: string) => void
  onExecute?: () => void
}

function CodeEditor({ value, onChange, onExecute }: Props) {
  const containerRef = useRef<HTMLDivElement>(null)
  const viewRef = useRef<EditorView | null>(null)
  const onChangeRef = useRef(onChange)
  const onExecuteRef = useRef(onExecute)

  // Keep refs current to avoid stale closures in extensions
  useEffect(() => { onChangeRef.current = onChange }, [onChange])
  useEffect(() => { onExecuteRef.current = onExecute }, [onExecute])

  useEffect(() => {
    if (!containerRef.current) return

    const executeKeymap = Prec.highest(
      keymap.of([
        {
          key: 'Ctrl-Enter',
          run: () => {
            onExecuteRef.current?.()
            return true
          },
        },
      ])
    )

    const updateListener = EditorView.updateListener.of(update => {
      if (update.docChanged) {
        onChangeRef.current(update.state.doc.toString())
      }
    })

    const editorTheme = EditorView.theme({
      '&': {
        height: '100%',
        fontSize: '13px',
        fontFamily: 'var(--font-mono)',
      },
      '.cm-scroller': {
        overflow: 'auto',
        fontFamily: 'inherit',
      },
      '.cm-content': {
        caretColor: 'var(--accent-primary)',
      },
      '&.cm-focused': {
        outline: '2px solid var(--a11y-focus-ring, var(--accent-primary))',
        outlineOffset: '-1px',
      },
      '.cm-cursor': {
        borderLeftColor: 'var(--accent-primary)',
      },
      '.cm-gutters': {
        background: 'var(--bg-secondary)',
        border: 'none',
        borderRight: '1px solid var(--border-light)',
      },
      '.cm-lineNumbers .cm-gutterElement': {
        color: 'var(--text-muted)',
        fontSize: '12px',
      },
      '.cm-activeLineGutter': {
        background: 'var(--bg-primary)',
      },
      '.cm-activeLine': {
        background: 'var(--accent-primary-bg)',
      },
      '.cm-selectionBackground': {
        background: 'var(--accent-primary-bg) !important',
      },
    })

    const view = new EditorView({
      doc: value,
      extensions: [
        basicSetup,
        python(),
        oneDark,
        editorTheme,
        commentContrastFix,
        executeKeymap,
        updateListener,
        // aria-label on the container div doesn't reach .cm-content, the
        // element axe (and a screen reader) actually treats as the textbox
        // (axe aria-input-field-name).
        EditorView.contentAttributes.of({ 'aria-label': 'Python code editor' }),
      ],
      parent: containerRef.current,
    })

    viewRef.current = view

    return () => {
      view.destroy()
      viewRef.current = null
    }
    // Only run on mount — external value changes are handled below
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Sync external value changes into the editor (e.g. script load)
  useEffect(() => {
    const view = viewRef.current
    if (!view) return
    const current = view.state.doc.toString()
    if (current !== value) {
      view.dispatch({
        changes: { from: 0, to: current.length, insert: value },
      })
    }
  }, [value])

  return (
    <div
      ref={containerRef}
      className="code-editor-container"
      aria-label="Python code editor"
    />
  )
}

export default CodeEditor
