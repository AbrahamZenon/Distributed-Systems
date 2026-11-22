#!/usr/bin/env python3
import os

def create_simple_pdf(filename, title, pages):
    """
    Generates a valid, multi-page PDF document without third-party dependencies.
    """
    objects = []
    
    def add_object(content):
        objects.append(content)
        return len(objects)

    # 1. Catalog (will be obj 1)
    # 2. Pages root (will be obj 2)
    # We will build page objects and content stream objects
    
    page_obj_ids = []
    content_obj_ids = []
    
    # Pre-reserve 1 and 2
    objects.append(None) # 1: Catalog
    objects.append(None) # 2: Pages root
    
    # Standard Font: Helvetica (obj 3)
    font_id = len(objects) + 1
    objects.append(
        f"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"
    )
    # Bold Font: Helvetica-Bold (obj 4)
    font_bold_id = len(objects) + 1
    objects.append(
        f"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>"
    )

    for page_lines in pages:
        # Build text stream
        stream_lines = ["BT"]
        y = 750
        stream_lines.append(f"/F2 16 Tf 50 {y} Td ({title}) Tj")
        y -= 25
        stream_lines.append(f"/F1 9 Tf 0 -25 Td (PTIT - Distributed Systems (DS04) | Author: Nguyen Duy Phuc - B23DCVT341) Tj")
        y -= 20
        stream_lines.append("0 -20 Td")
        
        for line in page_lines:
            # Escape parenthesis
            clean_line = line.replace('\\', '\\\\').replace('(', '\\(').replace(')', '\\)')
            if clean_line.startswith("### "):
                stream_lines.append(f"/F2 12 Tf ({clean_line[4:]}) Tj 0 -18 Td /F1 10 Tf")
            elif clean_line.startswith("## "):
                stream_lines.append(f"/F2 14 Tf ({clean_line[3:]}) Tj 0 -22 Td /F1 10 Tf")
            elif clean_line.startswith("# "):
                stream_lines.append(f"/F2 16 Tf ({clean_line[2:]}) Tj 0 -26 Td /F1 10 Tf")
            elif clean_line.startswith("- "):
                stream_lines.append(f"/F1 9.5 Tf (  * {clean_line[2:]}) Tj 0 -14 Td")
            elif clean_line.startswith("| "):
                stream_lines.append(f"/F1 8.5 Tf ({clean_line}) Tj 0 -12 Td")
            elif clean_line.strip() == "":
                stream_lines.append("0 -8 Td")
            else:
                # Normal paragraph line
                stream_lines.append(f"/F1 9.5 Tf ({clean_line}) Tj 0 -14 Td")

        stream_lines.append("ET")
        stream_content = "\n".join(stream_lines)
        stream_bytes = stream_content.encode('latin1', errors='replace')
        
        # Content stream obj
        c_id = len(objects) + 1
        objects.append(
            f"<< /Length {len(stream_bytes)} >>\nstream\n{stream_content}\nendstream"
        )
        content_obj_ids.append(c_id)
        
        # Page obj
        p_id = len(objects) + 1
        page_obj_ids.append(p_id)
        objects.append(
            f"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
            f"/Resources << /Font << /F1 {font_id} 0 R /F2 {font_bold_id} 0 R >> >> "
            f"/Contents {c_id} 0 R >>"
        )

    # Set Catalog (obj 1)
    objects[0] = f"<< /Type /Catalog /Pages 2 0 R >>"
    
    # Set Pages Root (obj 2)
    kids_str = " ".join(f"{pid} 0 R" for pid in page_obj_ids)
    objects[1] = f"<< /Type /Pages /Kids [{kids_str}] /Count {len(page_obj_ids)} >>"

    # Assemble PDF with cross-reference table
    output = bytearray(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")
    offsets = []
    
    for idx, obj in enumerate(objects, start=1):
        offsets.append(len(output))
        obj_str = f"{idx} 0 obj\n{obj}\nendobj\n"
        output.extend(obj_str.encode('latin1', errors='replace'))
        
    startxref = len(output)
    xref_str = f"xref\n0 {len(objects) + 1}\n0000000000 65535 f \n"
    for off in offsets:
        xref_str += f"{off:010d} 00000 n \n"
        
    xref_str += f"trailer\n<< /Size {len(objects) + 1} /Root 1 0 R >>\nstartxref\n{startxref}\n%%EOF\n"
    output.extend(xref_str.encode('latin1'))

    with open(filename, 'wb') as f:
        f.write(output)
    print(f"[+] Successfully generated: {filename} ({len(pages)} pages, {len(output)} bytes)")


def main():
    os.makedirs("docs", exist_ok=True)
    
    # 1. Parse REPORT.md into pages
    report_pages = []
    current_page = []
    line_count = 0
    
    if os.path.exists("docs/REPORT.md"):
        with open("docs/REPORT.md", "r", encoding="utf-8") as f:
            for raw_line in f:
                line = raw_line.strip()
                if line.startswith("---") or line.startswith("## ") and line_count > 25:
                    if current_page:
                        report_pages.append(current_page)
                        current_page = []
                        line_count = 0
                if line.startswith("---"):
                    continue
                current_page.append(line)
                line_count += 1
                if line_count >= 38:
                    report_pages.append(current_page)
                    current_page = []
                    line_count = 0
            if current_page:
                report_pages.append(current_page)
    else:
        report_pages = [["DS04 Technical Report", "Author: Nguyen Duy Phuc (B23DCVT341)"]]

    create_simple_pdf("docs/report.pdf", "DS04 Collaborative Editor - Technical Report", report_pages)

    # 2. Parse SLIDES.md into pages (1 slide = 1 page)
    slides_pages = []
    current_slide = []
    
    if os.path.exists("docs/SLIDES.md"):
        with open("docs/SLIDES.md", "r", encoding="utf-8") as f:
            for raw_line in f:
                line = raw_line.strip()
                if line.startswith("### SLIDE") or line.startswith("---") and len(current_slide) > 2:
                    if current_slide:
                        slides_pages.append(current_slide)
                        current_slide = []
                if line.startswith("---"):
                    continue
                current_slide.append(line)
            if current_slide:
                slides_pages.append(current_slide)
    else:
        slides_pages = [["DS04 Project Presentation", "Author: Nguyen Duy Phuc (B23DCVT341)"]]

    create_simple_pdf("docs/slides.pdf", "DS04 Project Presentation Slides", slides_pages)


if __name__ == "__main__":
    main()
