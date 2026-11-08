import zlib, struct, math

def create_png(width, height, get_pixel, filename):
    raw_data = bytearray()
    for y in range(height):
        raw_data.append(0)  # filter type None
        for x in range(width):
            r, g, b = get_pixel(x, y)
            raw_data.extend((r, g, b))
    compressed = zlib.compress(bytes(raw_data), 9)

    def chunk(tag, data):
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)

    png = b'\x89PNG\r\n\x1a\n'
    png += chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 2, 0, 0, 0))
    png += chunk(b'IDAT', compressed)
    png += chunk(b'IEND', b'')
    with open(filename, 'wb') as f:
        f.write(png)

# Simple raster font (5x7) for clean readable headers and labels in screenshots
FONT_5X7 = {
    ' ': [0,0,0,0,0],
    'A': [0x7E, 0x11, 0x11, 0x11, 0x7E],
    'B': [0x7F, 0x49, 0x49, 0x49, 0x36],
    'C': [0x3E, 0x41, 0x41, 0x41, 0x22],
    'D': [0x7F, 0x41, 0x41, 0x22, 0x1C],
    'E': [0x7F, 0x49, 0x49, 0x49, 0x41],
    'F': [0x7F, 0x09, 0x09, 0x09, 0x01],
    'G': [0x3E, 0x41, 0x49, 0x49, 0x7A],
    'H': [0x7F, 0x08, 0x08, 0x08, 0x7F],
    'I': [0x00, 0x41, 0x7F, 0x41, 0x00],
    'J': [0x20, 0x40, 0x41, 0x3F, 0x01],
    'K': [0x7F, 0x08, 0x14, 0x22, 0x41],
    'L': [0x7F, 0x40, 0x40, 0x40, 0x40],
    'M': [0x7F, 0x02, 0x0C, 0x02, 0x7F],
    'N': [0x7F, 0x04, 0x08, 0x10, 0x7F],
    'O': [0x3E, 0x41, 0x41, 0x41, 0x3E],
    'P': [0x7F, 0x09, 0x09, 0x09, 0x06],
    'Q': [0x3E, 0x41, 0x51, 0x21, 0x5E],
    'R': [0x7F, 0x09, 0x19, 0x29, 0x46],
    'S': [0x46, 0x49, 0x49, 0x49, 0x31],
    'T': [0x01, 0x01, 0x7F, 0x01, 0x01],
    'U': [0x3F, 0x40, 0x40, 0x40, 0x3F],
    'V': [0x1F, 0x20, 0x40, 0x20, 0x1F],
    'W': [0x7F, 0x20, 0x18, 0x20, 0x7F],
    'X': [0x63, 0x14, 0x08, 0x14, 0x63],
    'Y': [0x07, 0x08, 0x70, 0x08, 0x07],
    'Z': [0x61, 0x51, 0x49, 0x45, 0x43],
    '0': [0x3E, 0x51, 0x49, 0x45, 0x3E],
    '1': [0x00, 0x42, 0x7F, 0x40, 0x00],
    '2': [0x42, 0x61, 0x51, 0x49, 0x46],
    '3': [0x21, 0x41, 0x45, 0x4B, 0x31],
    '4': [0x18, 0x14, 0x12, 0x7F, 0x10],
    '5': [0x27, 0x45, 0x45, 0x45, 0x39],
    '6': [0x3C, 0x4A, 0x49, 0x49, 0x30],
    '7': [0x01, 0x71, 0x09, 0x05, 0x03],
    '8': [0x36, 0x49, 0x49, 0x49, 0x36],
    '9': [0x06, 0x49, 0x49, 0x29, 0x1E],
    ':': [0x00, 0x36, 0x36, 0x00, 0x00],
    '.': [0x00, 0x60, 0x60, 0x00, 0x00],
    ',': [0x00, 0x80, 0x60, 0x00, 0x00],
    '-': [0x08, 0x08, 0x08, 0x08, 0x08],
    '+': [0x08, 0x08, 0x3E, 0x08, 0x08],
    '/': [0x20, 0x10, 0x08, 0x04, 0x02],
    '(': [0x00, 0x1C, 0x22, 0x41, 0x00],
    ')': [0x00, 0x41, 0x22, 0x1C, 0x00],
    '%': [0x23, 0x13, 0x08, 0x64, 0x62],
    '|': [0x00, 0x00, 0x7F, 0x00, 0x00],
}

class Canvas:
    def __init__(self, w, h, bg=(254, 247, 255)):
        self.w = w
        self.h = h
        self.pixels = [[bg for _ in range(w)] for _ in range(h)]

    def fill_rect(self, x1, y1, x2, y2, color):
        x1, y1 = max(0, int(x1)), max(0, int(y1))
        x2, y2 = min(self.w - 1, int(x2)), min(self.h - 1, int(y2))
        for y in range(y1, y2 + 1):
            for x in range(x1, x2 + 1):
                self.pixels[y][x] = color

    def fill_round_rect(self, x1, y1, x2, y2, r, color):
        for y in range(int(y1), int(y2) + 1):
            for x in range(int(x1), int(x2) + 1):
                if 0 <= x < self.w and 0 <= y < self.h:
                    dx = max(0, abs(x - (x1 + x2) / 2) - ((x2 - x1) / 2 - r))
                    dy = max(0, abs(y - (y1 + y2) / 2) - ((y2 - y1) / 2 - r))
                    if dx * dx + dy * dy <= r * r:
                        self.pixels[y][x] = color

    def draw_text(self, text, x, y, color, scale=2):
        text = text.upper()
        cx = x
        for char in text:
            cols = FONT_5X7.get(char, FONT_5X7[' '])
            for col_idx, col_bits in enumerate(cols):
                for row_idx in range(7):
                    if (col_bits >> row_idx) & 1:
                        for sy in range(scale):
                            for sx in range(scale):
                                px = cx + col_idx * scale + sx
                                py = y + row_idx * scale + sy
                                if 0 <= px < self.w and 0 <= py < self.h:
                                    self.pixels[py][px] = color
            cx += 6 * scale

    def save(self, path):
        create_png(self.w, self.h, lambda x, y: self.pixels[y][x], path)

# 1. Homepage (CrestWave empty state)
def render_homepage(path):
    c = Canvas(800, 520, bg=(250, 247, 252))
    # Top bar
    c.fill_rect(0, 0, 799, 56, (244, 238, 247))
    # "+ Add" tonal button
    c.fill_round_rect(16, 12, 100, 44, 16, (230, 222, 238))
    c.draw_text("+ ADD", 32, 21, (79, 55, 139), scale=2)
    # Centered Title "CRESTWAVE"
    c.draw_text("CRESTWAVE", 345, 20, (30, 27, 32), scale=2)
    # Right buttons: Keyring & Menu
    c.fill_round_rect(700, 12, 736, 44, 16, (230, 222, 238))
    c.draw_text("KEY", 706, 21, (79, 55, 139), scale=1)
    c.fill_round_rect(746, 12, 782, 44, 16, (230, 222, 238))
    c.draw_text("MENU", 750, 21, (79, 55, 139), scale=1)
    # Divider
    c.fill_rect(0, 56, 799, 57, (225, 218, 230))

    # Center Emblem: CrestWave squircle
    c.fill_round_rect(340, 130, 460, 250, 28, (214, 237, 237))
    # Wave and download arrow representation inside emblem
    c.fill_round_rect(365, 180, 435, 188, 4, (46, 196, 182))
    c.fill_round_rect(375, 196, 425, 202, 3, (72, 202, 228))
    c.fill_round_rect(395, 150, 405, 185, 4, (255, 255, 255))
    c.fill_round_rect(385, 175, 415, 182, 3, (255, 255, 255))

    # Center Headline
    c.draw_text("DOWNLOAD MEDIA", 285, 285, (30, 27, 32), scale=3)
    c.draw_text("ADD A VIDEO, AUDIO, OR PLAYLIST URL TO START DOWNLOADING", 145, 330, (115, 108, 122), scale=2)

    # Center CTA: Tonal "+ Add Download" button
    c.fill_round_rect(300, 375, 500, 425, 24, (230, 222, 238))
    c.draw_text("+ ADD DOWNLOAD", 325, 393, (79, 55, 139), scale=2)

    c.save(path)

# 2. Add Download Sheet
def render_add_download(path):
    c = Canvas(800, 520, bg=(250, 247, 252))
    # Top bar
    c.fill_rect(0, 0, 799, 56, (244, 238, 247))
    c.fill_round_rect(16, 12, 100, 44, 16, (230, 222, 238))
    c.draw_text("< BACK", 28, 21, (79, 55, 139), scale=2)
    c.draw_text("ADD DOWNLOAD", 325, 20, (30, 27, 32), scale=2)
    c.fill_round_rect(670, 12, 782, 44, 16, (91, 63, 166))
    c.draw_text("DOWNLOAD", 685, 21, (255, 255, 255), scale=2)
    c.fill_rect(0, 56, 799, 57, (225, 218, 230))

    # Media URL Input card
    c.fill_round_rect(24, 75, 776, 170, 16, (255, 255, 255))
    c.draw_text("MEDIA URL", 40, 90, (79, 55, 139), scale=2)
    c.fill_round_rect(40, 115, 630, 155, 8, (245, 242, 248))
    c.draw_text("HTTPS://YOUTUBE.COM/WATCH?V=EXAMPLE", 55, 128, (60, 55, 65), scale=2)
    c.fill_round_rect(645, 115, 760, 155, 8, (230, 222, 238))
    c.draw_text("INSPECT", 670, 128, (79, 55, 139), scale=2)

    # Container & Format Card
    c.fill_round_rect(24, 185, 776, 335, 16, (255, 255, 255))
    c.draw_text("CONTAINER & FORMAT", 40, 200, (79, 55, 139), scale=2)

    # Format chips
    c.draw_text("VIDEO CONTAINERS:", 40, 230, (115, 108, 122), scale=1)
    chips = ["MP4 (SELECTED)", "WEBM", "MKV"]
    cx = 40
    for i, chip in enumerate(chips):
        bg = (91, 63, 166) if i == 0 else (240, 235, 245)
        fg = (255, 255, 255) if i == 0 else (70, 65, 75)
        c.fill_round_rect(cx, 248, cx + len(chip)*10 + 16, 276, 12, bg)
        c.draw_text(chip, cx + 10, 257, fg, scale=1)
        cx += len(chip)*10 + 26

    c.draw_text("AUDIO-ONLY CONTAINERS:", 40, 290, (115, 108, 122), scale=1)
    achips = ["MP3", "OPUS", "FLAC", "WAV", "M4A"]
    cx = 40
    for chip in achips:
        c.fill_round_rect(cx, 305, cx + len(chip)*10 + 16, 330, 12, (240, 235, 245))
        c.draw_text(chip, cx + 10, 314, (70, 65, 75), scale=1)
        cx += len(chip)*10 + 24

    # Resolution & Features Card
    c.fill_round_rect(24, 350, 776, 500, 16, (255, 255, 255))
    c.draw_text("RESOLUTION & OPTIONS", 40, 365, (79, 55, 139), scale=2)
    rchips = ["BEST", "2160P (4K)", "1440P", "1080P (FHD)", "720P"]
    cx = 40
    for i, chip in enumerate(rchips):
        bg = (91, 63, 166) if i == 3 else (240, 235, 245)
        fg = (255, 255, 255) if i == 3 else (70, 65, 75)
        c.fill_round_rect(cx, 390, cx + len(chip)*9 + 16, 416, 12, bg)
        c.draw_text(chip, cx + 8, 399, fg, scale=1)
        cx += len(chip)*9 + 22

    # Toggle options
    c.draw_text("EMBED METADATA: ON   |   EMBED SUBTITLES: ON   |   TRIM / CLIP: OFF", 40, 445, (85, 80, 92), scale=2)
    c.draw_text("DESTINATION: DOWNLOADS/CRESTWAVE", 40, 475, (120, 115, 125), scale=2)

    c.save(path)

# 3. Active Queue & Live Log Workspace
def render_active_queue(path):
    c = Canvas(800, 520, bg=(250, 247, 252))
    # Top bar
    c.fill_rect(0, 0, 799, 56, (244, 238, 247))
    c.fill_round_rect(16, 12, 100, 44, 16, (230, 222, 238))
    c.draw_text("+ ADD", 32, 21, (79, 55, 139), scale=2)
    c.draw_text("CRESTWAVE", 345, 20, (30, 27, 32), scale=2)
    c.fill_round_rect(700, 12, 736, 44, 16, (230, 222, 238))
    c.draw_text("KEY", 706, 21, (79, 55, 139), scale=1)
    c.fill_round_rect(746, 12, 782, 44, 16, (230, 222, 238))
    c.draw_text("MENU", 750, 21, (79, 55, 139), scale=1)
    c.fill_rect(0, 56, 799, 57, (225, 218, 230))

    # Split View: Left list pane (360px), Right detail pane (420px)
    c.fill_round_rect(16, 70, 360, 500, 12, (255, 255, 255))
    c.draw_text("ACTIVE DOWNLOADS (2)", 30, 85, (79, 55, 139), scale=2)

    # Item 1: Downloading
    c.fill_round_rect(24, 115, 350, 210, 8, (244, 238, 248))
    c.draw_text("CRESTWAVE TUTORIAL VIDEO", 34, 125, (30, 27, 32), scale=2)
    c.draw_text("MP4  -  1080P  -  DOWNLOADING", 34, 145, (100, 95, 105), scale=1)
    # Progress bar
    c.fill_round_rect(34, 165, 340, 173, 4, (220, 215, 225))
    c.fill_round_rect(34, 165, 240, 173, 4, (91, 63, 166))
    c.draw_text("65% - 4.2 MB/S - ETA 00:24", 34, 185, (91, 63, 166), scale=1)

    # Item 2: Completed
    c.fill_round_rect(24, 225, 350, 305, 8, (244, 238, 248))
    c.draw_text("OPEN SOURCE DOCUMENTARY", 34, 235, (30, 27, 32), scale=2)
    c.draw_text("MKV  -  1440P  -  COMPLETED", 34, 255, (0, 106, 106), scale=1)
    c.fill_round_rect(34, 275, 340, 283, 4, (0, 106, 106))
    c.draw_text("100% - EXPORTED TO MEDIASTORE", 34, 290, (0, 106, 106), scale=1)

    # Right Detail / Process Log Pane
    c.fill_round_rect(376, 70, 784, 500, 12, (255, 255, 255))
    c.draw_text("JOB DETAILS & LIVE LOG", 392, 85, (79, 55, 139), scale=2)

    # Actions: Pause, Cancel, Open
    c.fill_round_rect(392, 115, 470, 145, 8, (230, 222, 238))
    c.draw_text("PAUSE", 412, 124, (79, 55, 139), scale=1)
    c.fill_round_rect(480, 115, 560, 145, 8, (230, 222, 238))
    c.draw_text("CANCEL", 498, 124, (79, 55, 139), scale=1)
    c.fill_round_rect(570, 115, 650, 145, 8, (91, 63, 166))
    c.draw_text("OPEN", 595, 124, (255, 255, 255), scale=1)

    # Log terminal box
    c.fill_round_rect(392, 160, 768, 485, 8, (20, 18, 24))
    logs = [
        "[INFO] YT-DLP INITIALIZED WITH NATIVE FFMPEG AND ARIA2C",
        "[EXTRACT] FETCHING MEDIA PLAYLIST/VIDEO METADATA",
        "[DOWNLOAD] DESTINATION: STAGING/DOWNLOADS/UUID-74F2",
        "[DOWNLOAD] 1080P VIDEO STREAM SELECTED (AVC1 / 60FPS)",
        "[DOWNLOAD] AUDIO STREAM SELECTED (OPUS 160KBPS)",
        "[DOWNLOAD] 65.2% OF ~142.5MB AT 4.2MB/S ETA 00:24",
        "[MERGE] MERGING VIDEO AND AUDIO INTO MKV INTERMEDIATE",
        "[EXPORT] ATOMIC COPY TO MEDIASTORE DOWNLOADS/CRESTWAVE",
    ]
    ly = 175
    for line in logs:
        c.draw_text(line, 404, ly, (200, 195, 210), scale=1)
        ly += 22

    c.save(path)

# 4. Keyring & Settings Screen
def render_settings(path):
    c = Canvas(800, 520, bg=(250, 247, 252))
    # Top bar
    c.fill_rect(0, 0, 799, 56, (244, 238, 247))
    c.fill_round_rect(16, 12, 100, 44, 16, (230, 222, 238))
    c.draw_text("< BACK", 28, 21, (79, 55, 139), scale=2)
    c.draw_text("PREFERENCES & KEYRING", 285, 20, (30, 27, 32), scale=2)
    c.fill_rect(0, 56, 799, 57, (225, 218, 230))

    # Concurrency Card
    c.fill_round_rect(24, 75, 776, 175, 16, (255, 255, 255))
    c.draw_text("CONCURRENT DOWNLOAD WORKERS", 40, 90, (79, 55, 139), scale=2)
    c.draw_text("MAXIMUM SIMULTANEOUS SLOTS (1 TO 4):", 40, 120, (115, 108, 122), scale=1)
    cx = 40
    for count in [1, 2, 3, 4]:
        bg = (91, 63, 166) if count == 2 else (240, 235, 245)
        fg = (255, 255, 255) if count == 2 else (70, 65, 75)
        label = f"{count} JOBS"
        c.fill_round_rect(cx, 135, cx + 80, 162, 12, bg)
        c.draw_text(label, cx + 14, 144, fg, scale=1)
        cx += 95

    # Keyring & Cookie Card
    c.fill_round_rect(24, 190, 776, 320, 16, (255, 255, 255))
    c.draw_text("KEYRING & AUTHENTICATION", 40, 205, (79, 55, 139), scale=2)
    c.draw_text("IMPORT NETSCAPE COOKIES.TXT OR LOG IN WITH ISOLATED WEBVIEW", 40, 235, (115, 108, 122), scale=1)
    c.fill_round_rect(40, 260, 240, 298, 12, (230, 222, 238))
    c.draw_text("IMPORT COOKIES.TXT", 55, 273, (79, 55, 139), scale=1)
    c.fill_round_rect(255, 260, 435, 298, 12, (230, 222, 238))
    c.draw_text("WEBVIEW SIGN-IN", 275, 273, (79, 55, 139), scale=1)

    # Engine Status Card
    c.fill_round_rect(24, 335, 776, 495, 16, (255, 255, 255))
    c.draw_text("EXTRACTION ENGINE & UPDATER", 40, 350, (79, 55, 139), scale=2)
    c.draw_text("BUNDLED YT-DLP, FFMPEG, AND ARIA2C WITH LIVE SERIALIZED UPDATER", 40, 380, (115, 108, 122), scale=1)
    c.fill_round_rect(40, 420, 280, 460, 16, (91, 63, 166))
    c.draw_text("UPDATE YT-DLP ENGINE", 55, 434, (255, 255, 255), scale=1)

    c.save(path)

render_homepage('/storage/emulated/10/Documents/Parabolic-Android/docs/screenshots/01_homepage.png')
render_add_download('/storage/emulated/10/Documents/Parabolic-Android/docs/screenshots/02_add_download.png')
render_active_queue('/storage/emulated/10/Documents/Parabolic-Android/docs/screenshots/03_active_queue.png')
render_settings('/storage/emulated/10/Documents/Parabolic-Android/docs/screenshots/04_preferences.png')
print('All 4 screenshots generated successfully.')
