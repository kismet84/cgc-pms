import markdown
from weasyprint import HTML
from pathlib import Path

md_file = Path(r"d:\projects-test\cgc-pms\doc\开发文档_v2.3\01_项目总体方案与业务闭环设计.md")
pdf_file = Path(r"d:\projects-test\cgc-pms\doc\开发文档_v2.3\01_项目总体方案与业务闭环设计.pdf")

with open(md_file, 'r', encoding='utf-8') as f:
    md_content = f.read()

html_content = markdown.markdown(md_content, extensions=['tables'])

html_with_style = f"""
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <style>
        body {{ font-family: SimSun, 'Microsoft YaHei', sans-serif; margin: 2cm; font-size: 11pt; }}
        h1 {{ color: #333; border-bottom: 2px solid #333; padding-bottom: 10px; font-size: 18pt; }}
        h2 {{ color: #444; margin-top: 20px; font-size: 14pt; }}
        h3 {{ color: #555; font-size: 12pt; }}
        table {{ border-collapse: collapse; width: 100%; margin: 10px 0; font-size: 10pt; }}
        th, td {{ border: 1px solid #ddd; padding: 6px 8px; text-align: left; }}
        th {{ background-color: #f2f2f2; }}
        code {{ background-color: #f4f4f4; padding: 2px 4px; border-radius: 3px; font-family: Consolas, monospace; font-size: 9pt; }}
        pre {{ background-color: #f4f4f4; padding: 10px; border-radius: 5px; overflow-x: auto; font-family: Consolas, monospace; font-size: 9pt; }}
        ul, ol {{ margin-left: 20px; line-height: 1.6; }}
        p {{ line-height: 1.8; margin: 8px 0; }}
        hr {{ border: none; border-top: 1px solid #ddd; margin: 20px 0; }}
        tr:nth-child(even) {{ background-color: #f9f9f9; }}
    </style>
</head>
<body>
{html_content}
</body>
</html>
"""

HTML(string=html_with_style).write_pdf(pdf_file)

print(f"PDF generated successfully: {pdf_file}")