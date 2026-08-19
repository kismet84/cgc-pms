const SCREEN_PREVIEW_STYLE =
  '<style data-document-screen-preview>.page-header,.page-footer{height:0!important;min-height:0!important;overflow:visible!important}</style>'

export function documentScreenPreviewHtml(html: string): string {
  if (!html || html.includes('data-document-screen-preview')) return html
  const headEnd = html.toLowerCase().indexOf('</head>')
  return headEnd < 0
    ? `${SCREEN_PREVIEW_STYLE}${html}`
    : `${html.slice(0, headEnd)}${SCREEN_PREVIEW_STYLE}${html.slice(headEnd)}`
}
