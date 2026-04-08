import os
from PIL import Image, ImageChops

# Настройки путей
TEMPLATE_DIR = "./templates/assets"
OUTPUT_DIR = "./src/main/resources/assets/omnitech/textures/item"
# Если нужно красить и блоки (руды, блоки), можно добавить путь отдельно
BLOCK_OUTPUT_DIR = "./src/main/resources/assets/omnitech/textures/block"

# Словарь материалов и их HEX-цветов (тинтов)
# Я подобрал примерные цвета, можешь подправить под свой вкус
MATERIALS = {
    "tungsten": "#444B52",
    "chromium": "#E1E8EB",
    "tin": "#C5D3D6",
    "aluminium": "#DEE9ED",
    "cobalt": "#2A52BE",
    "nickel": "#D4E1C6",
    "zinc": "#BFCED1",
    "brass": "#E1C16E",
    "iron": "#D8D8D8",
    "copper": "#E77D55",
    "sodium_chlorine": "#FFFFFF",
    "graphite": "#2F2F2F",
    "skutterudite": "#919191",
    "galena": "#5A5D6E",
    "halite": "#F2E8D5",
    "sphalerite": "#8B6D4F",
    "lepidolite": "#D1A3D1"
}

# Список вариаций из твоего Java кода
# Ключ - шаблон в Java, Значение - название базового файла текстуры (без .png)
VARIATIONS = [
    "raw_%", "%_ingot", "%_dust", "%_plate", "%_mote", 
    "%_nugget", "%_reductor", "%_cog", "%_wire", 
    "%_coil", "%_rod", "%_ore", "%_block", "raw_%_block"
]

def hex_to_rgb(hex_str):
    hex_str = hex_str.lstrip('#')
    return tuple(int(hex_str[i:i+2], 16) for i in (0, 2, 4))

def process_textures():
    if not os.path.exists(OUTPUT_DIR): os.makedirs(OUTPUT_DIR)
    if not os.path.exists(BLOCK_OUTPUT_DIR): os.makedirs(BLOCK_OUTPUT_DIR)

    for mat_name, hex_color in MATERIALS.items():
        rgb_color = hex_to_rgb(hex_color)
        
        for pattern in VARIATIONS:
            # Определяем имя итогового файла
            file_name = pattern.replace("%", mat_name) + ".png"
            
            # Определяем, куда класть: в блоки или айтемы
            is_block = any(x in pattern for x in ["ore", "_block"])
            save_path = os.path.join(BLOCK_OUTPUT_DIR if is_block else OUTPUT_DIR, file_name)

            # Пропускаем, если файл уже существует
            if os.path.exists(save_path):
                print(f"[-] Skipping {file_name} (already exists)")
                continue

            # Определяем имя шаблона (например, %_dust -> default_dust.png)
            template_suffix = pattern.replace("%", "default")
            template_path = os.path.join(TEMPLATE_DIR, f"{template_suffix}.png")

            if not os.path.exists(template_path):
                # Если специфичного шаблона нет, попробуем найти по базовому типу
                # (например, если у тебя один default_ore.png на все типы руд)
                print(f"[!] Template missing: {template_path}")
                continue

            try:
                # Открываем шаблон
                with Image.open(template_path).convert("RGBA") as img:
                    # Создаем слой заливки цветом
                    tint_layer = Image.new("RGBA", img.size, rgb_color + (255,))
                    
                    # Применяем тинт методом умножения (сохраняет тени)
                    # Используем ImageChops.multiply
                    # Но чтобы не красить прозрачные пиксели, используем маску
                    result = ImageChops.multiply(img, tint_layer)
                    
                    # Возвращаем оригинальный альфа-канал
                    result.putalpha(img.getchannel('A'))
                    
                    # Сохраняем
                    result.save(save_path)
                    print(f"[+] Generated {save_path}")
            except Exception as e:
                print(f"[X] Error processing {file_name}: {e}")

if __name__ == "__main__":
    process_textures()