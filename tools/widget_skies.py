"""Draws the still pictures the widgets use as their sky, one per background style, weather, time of day and theme.

The widgets can't run the web app's animated scenes, so this script draws them once from the bundled web app
(app/src/main/assets/web/index.html) with Chromium and saves them as WebP files under
app/src/main/assets/widget-sky/<style>/. Run it again whenever a background style changes:

    pip install playwright pillow && python3 tools/widget_skies.py

Painterly, Dynamic and Illustration show the app's page sky (sun or moon, clouds, rain, snow); Mt Hood shows the
wide Today card scene, which carries the mountain, the lake and the snow line for the season. Basic needs no
pictures: the widgets draw its sky themselves.
"""
import asyncio
import io
import pathlib
import shutil
import tempfile

from PIL import Image
from playwright.async_api import async_playwright

ROOT = pathlib.Path(__file__).resolve().parent.parent
PAGE = ROOT / "app/src/main/assets/web/index.html"
OUT = ROOT / "app/src/main/assets/widget-sky"
W, H, SCALE, QUALITY = 640, 300, 1.5, 80

CATS = ["clear", "partly", "cloudy", "fog", "drizzle", "rain", "snow", "storm"]
PAGE_STYLES = ["painterly", "realistic", "illustration"]
PAGE_TIMES = {"day": (1, False), "dawn": (.5, True), "dusk": (.5, False), "night": (0, False)}
HOOD_TIMES = {"day": (1, False), "dusk": (.5, False), "night": (0, False)}
HOOD_SEASONS = {"third": "2026-10-15", "half": "2026-04-15", "twothirds": "2026-01-15"}

# Shows one scene in a W x H box at the top-left of the page, drawn the way the app layers it
DRAW = """([style, cat, d, rising, ymd]) => {
  const S = window.__aweSky, st = S.styles.find((s) => s.id === style), ph = S.moonPhase(Date.parse("2026-10-22T12:00:00Z"));
  const light = { d, rising }, cs = getComputedStyle(document.body);
  let back, layer, op, svg;
  if (style === "mthood") {
    const c = st.card(cat, 1, true, { ymd, light, ph });
    back = cs.getPropertyValue("--surface"); layer = "transparent"; op = c.op;
    svg = c.svg.slice(c.svg.indexOf('<svg class="sc-wide"')).replace('preserveAspectRatio="xMidYMax slice"', 'preserveAspectRatio="xMidYMid slice"');
  } else {
    const p = st.page(cat, light, ph, false, ymd);
    back = cs.backgroundColor; layer = p.sky; op = p.op;
    svg = `<svg viewBox="960 0 640 300" preserveAspectRatio="xMidYMid slice">${p.svg}</svg>`;
  }
  let box = document.getElementById("skyBox");
  if (!box) { box = document.createElement("div"); box.id = "skyBox"; document.body.appendChild(box); }
  box.style.cssText = `position:fixed;left:0;top:0;width:${W}px;height:${H}px;z-index:99999;background:${back}`;
  box.innerHTML = `<div style="position:absolute;inset:0;background:${layer};opacity:${op}">${svg}</div>`;
  box.querySelector("svg").style.cssText = "position:absolute;inset:0;width:100%;height:100%";
}""".replace("${W}", str(W)).replace("${H}", str(H))


async def main():
    tmp = pathlib.Path(tempfile.mkdtemp())
    # A private copy of the page that hands its styles to this script; the bundled page itself is untouched
    src = PAGE.read_text(encoding="utf-8")
    hook = "window.awesomeWeather = {"
    assert src.count(hook) == 1
    (tmp / "index.html").write_text(src.replace(hook, "window.__aweSky = { styles: BG_STYLES, moonPhase };\n  " + hook), encoding="utf-8")
    shutil.rmtree(OUT, ignore_errors=True)
    jobs = [(s, c, t, PAGE_TIMES[t], None, f"{c}-{t}") for s in PAGE_STYLES for c in CATS for t in PAGE_TIMES]
    jobs += [("mthood", c, t, HOOD_TIMES[t], ymd, f"{c}-{t}-{season}") for c in CATS for t in HOOD_TIMES for season, ymd in HOOD_SEASONS.items()]
    total = 0
    async with async_playwright() as p:
        browser = await p.chromium.launch()
        for theme in ["light", "dark"]:
            ctx = await browser.new_context(viewport={"width": W, "height": H}, device_scale_factor=SCALE, color_scheme=theme)
            page = await ctx.new_page()
            await page.route("**/*", lambda r: r.continue_() if r.request.url.startswith("file:") else r.abort())
            await page.goto((tmp / "index.html").as_uri())
            await page.add_style_tag(content="*, *::before, *::after { animation: none !important; transition: none !important; }")
            for style, cat, tod, (d, rising), ymd, key in jobs:
                await page.evaluate(DRAW, [style, cat, d, rising, ymd])
                png = await page.screenshot(clip={"x": 0, "y": 0, "width": W, "height": H})
                path = OUT / style / f"{key}-{theme}.webp"
                path.parent.mkdir(parents=True, exist_ok=True)
                Image.open(io.BytesIO(png)).convert("RGB").save(path, "WEBP", quality=QUALITY, method=6)
                total += path.stat().st_size
            await ctx.close()
        await browser.close()
    shutil.rmtree(tmp)
    print(f"{len(jobs) * 2} pictures, {total / 1e6:.1f} MB")


asyncio.run(main())
