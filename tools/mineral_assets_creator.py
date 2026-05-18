import os
import yaml
import json
import random
from pathlib import Path
from PIL import Image, ImageDraw

_SCRIPT_DIR  = Path(__file__).parent
BASE_PATH    = _SCRIPT_DIR.parent / "src/main/resources/assets/omnitech"
TEXTURE_PATH = str(BASE_PATH / "textures/block")
MODEL_PATH   = str(BASE_PATH / "models/block")
ITEM_PATH    = str(BASE_PATH / "items")
CONFIG_FILE  = str(_SCRIPT_DIR / "minerals.yml")

def hex_to_rgb(hex_color):
    hex_color = hex_color.lstrip('#')
    return tuple(int(hex_color[i:i+2], 16) for i in (0, 2, 4))

def generate_ore_texture(mineral):
    size = 16
    img = Image.new("RGBA", (size, size))
    draw = ImageDraw.Draw(img)

    # 1. Генерируем базу (камень)
    base_rgb = hex_to_rgb(mineral['visuals']['base_layer']['color'])
    for x in range(size):
        for y in range(size):
            noise = random.randint(-15, 15)
            color = tuple(max(0, min(255, c + noise)) for c in base_rgb)
            draw.point((x, y), fill=color)

    # 2. Накладываем вкрапления (inclusions)
    for inclusion in mineral['visuals'].get('inclusions', []):
        inc_rgb = hex_to_rgb(inclusion['color'])
        density = inclusion.get('density', 0.3)
        
        pixel_count = int(size * size * density)
        
        for _ in range(pixel_count):
            x, y = random.randint(0, size-1), random.randint(0, size-1)
            
            s_min = int(inclusion['size']['min'] * 5)
            s_max = int(inclusion['size']['max'] * 5)
            spot_size = random.randint(max(1, s_min), max(1, s_max))
            
            for i in range(spot_size):
                for j in range(spot_size):
                    if 0 <= x+i < size and 0 <= y+j < size:
                        noise = random.randint(-20, 20)
                        final_c = tuple(max(0, min(255, c + noise)) for c in inc_rgb)
                        draw.point((x+i, y+j), fill=final_c)
    
    return img

def save_json(path, data):
    with open(path, 'w') as f:
        json.dump(data, f, indent=2)

def main():
    # Создаем директории если их нет
    for p in [TEXTURE_PATH, MODEL_PATH, ITEM_PATH]:
        os.makedirs(p, exist_ok=True)

    if not os.path.exists(CONFIG_FILE):
        print(f"[error] {CONFIG_FILE} not found!")
        return

    with open(CONFIG_FILE, 'r') as f:
        config = yaml.safe_load(f)

    for mineral in config['minerals']:
        name = mineral['name']
        tex_file = f"{TEXTURE_PATH}/{name}.png"
        model_file = f"{MODEL_PATH}/{name}.json"
        item_file = f"{ITEM_PATH}/{name}.json"

        # 1. Текстура
        if not os.path.exists(tex_file):
            img = generate_ore_texture(mineral)
            img.save(tex_file)
            print(f"[added] texture: {name}")
        else:
            print(f"[skipped] texture: {name}")

        # 2. Модель блока
        if not os.path.exists(model_file):
            block_model = {
                "parent": "minecraft:block/cube_all",
                "textures": {
                    "all": f"omnitech:block/{name}"
                }
            }
            save_json(model_file, block_model)
            print(f"[added] model: {name}")
        else:
            print(f"[skipped] model: {name}")

        # 3. Модель предмета
        if not os.path.exists(item_file):
            item_model = {
                "model": {
                    "type": "minecraft:model",
                    "model": f"omnitech:block/{name}"
                }
            }
            save_json(item_file, item_model)
            print(f"[added] item: {name}")
        else:
            print(f"[skipped] item: {name}")

if __name__ == "__main__":
    main()