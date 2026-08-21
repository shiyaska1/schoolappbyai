from PIL import Image, ImageDraw, ImageFont

BG = "#1565C0"
FG = "#FFFFFF"
SS = 4  # supersample factor for antialiasing

def cubic_bezier(p0, p1, p2, p3, n=24):
    pts = []
    for i in range(n + 1):
        t = i / n
        mt = 1 - t
        x = mt**3*p0[0] + 3*mt**2*t*p1[0] + 3*mt*t**2*p2[0] + t**3*p3[0]
        y = mt**3*p0[1] + 3*mt**2*t*p1[1] + 3*mt*t**2*p2[1] + t**3*p3[1]
        pts.append((x, y))
    return pts

def build_shapes():
    # Shape 1: M54,24 L86,40 L54,56 L22,40 Z
    shape1 = [(54,24),(86,40),(54,56),(22,40)]
    # Shape 2: M34,48 L34,66 C34,72 43,78 54,78 C65,78 74,72 74,66 L74,48 L54,58 Z
    shape2 = [(34,48),(34,66)]
    shape2 += cubic_bezier((34,66),(34,72),(43,78),(54,78))[1:]
    shape2 += cubic_bezier((54,78),(65,78),(74,72),(74,66))[1:]
    shape2 += [(74,48),(54,58)]
    # Shape 3: M84,40 L86,40 L86,64 L84,64 Z
    shape3 = [(84,40),(86,40),(86,64),(84,64)]
    return [shape1, shape2, shape3]

def render_icon(size, out_path, rounded=False, padding_frac=0.0):
    big = size * SS
    img = Image.new("RGBA", (big, big), BG)
    d = ImageDraw.Draw(img)
    scale = big / 108
    pad = big * padding_frac
    for shape in build_shapes():
        pts = [(x*scale, y*scale) for x, y in shape]
        d.polygon(pts, fill=FG)
    img = img.resize((size, size), Image.LANCZOS)
    if rounded:
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).ellipse((0,0,size,size), fill=255)
        out = Image.new("RGBA", (size, size), (0,0,0,0))
        out.paste(img, (0,0), mask)
        img = out
    img.save(out_path)
    print("wrote", out_path)

def render_feature_graphic(out_path):
    w, h = 1024, 500
    ss = 2
    img = Image.new("RGB", (w*ss, h*ss), BG)
    d = ImageDraw.Draw(img)
    # icon on the left
    icon_size = 300 * ss
    icon = Image.new("RGBA", (icon_size, icon_size), (0,0,0,0))
    idraw = ImageDraw.Draw(icon)
    scale = icon_size / 108
    for shape in build_shapes():
        pts = [(x*scale, y*scale) for x, y in shape]
        idraw.polygon(pts, fill=FG)
    img.paste(icon, (80*ss, (h*ss - icon_size)//2), icon)
    # title text
    try:
        font = ImageFont.truetype("segoeuib.ttf", 46*ss)
        font_small = ImageFont.truetype("segoeui.ttf", 24*ss)
    except Exception:
        font = ImageFont.load_default()
        font_small = font
    tx = (80 + 300 + 30) * ss
    d.text((tx, 185*ss), "School Management App", fill=FG, font=font)
    d.text((tx, 250*ss), "Attendance · Exams · Accounts · Bus Tracking", fill=FG, font=font_small)
    img = img.resize((w, h), Image.LANCZOS)
    img.save(out_path)
    print("wrote", out_path)

if __name__ == "__main__":
    import os
    outdir = os.path.join(os.path.dirname(__file__), "..", "playstore-assets")
    os.makedirs(outdir, exist_ok=True)
    render_icon(512, os.path.join(outdir, "icon-512.png"))
    render_feature_graphic(os.path.join(outdir, "feature-graphic-1024x500.png"))
