import os
from PIL import Image, ImageChops, ImageEnhance, ImageDraw

# Настройки путей
TEMPLATE_DIR = "./templates/assets"
OUTPUT_DIR = "./src/main/resources/assets/omnitech/textures/item"
BLOCK_OUTPUT_DIR = "./src/main/resources/assets/omnitech/textures/block"

# Материалы: "название": (Основа, Блик, Яркость, Контраст)
MATERIALS = {
    # Металлы с характерным отблеском
    "tungsten":        ("#31363B", "#5E6770", 0.9, 1.2), # Темный металл, серый блеск
    "chromium":        ("#C0C9CC", "#FFFFFF", 1.2, 1.4), # Хром: от светло-серого к чисто белому
    "tin":             ("#9BA9AB", "#DDE9EB", 1.0, 1.1), # Олово: голубовато-серый
    "aluminium":       ("#B2BFC2", "#FFFFFF", 1.1, 1.3), # Алюминий: яркий белый блеск
    "cobalt":          ("#1E3A8A", "#00FF3C", 1.0, 1.4), # Кобальт: синий с голубым бликом
    "nickel":          ("#A8B59A", "#E6EFD3", 1.0, 1.1), # Никель: желтовато-зеленый отлив
    "zinc":            ("#8D9FA3", "#C7D6D9", 1.0, 1.0), 
    "brass":           ("#A68521", "#FDE68A", 1.1, 1.3), # Латунь: от темного золота к лимонному
    "iron":            ("#999999", "#D9D9D9", 1.0, 1.1),
    "copper":          ("#B05130", "#F9A886", 1.0, 1.3), # Медь: от кирпичного к нежно-розовому
    
    # Неметаллы / Порошки
    "sodium_chlorine": ("#E5E5E5", "#FFFFFF", 1.0, 0.8), # Соль: матовая
    "graphite":        ("#1A1A1A", "#404040", 0.8, 0.7), # Графит: почти черный
    "skutterudite":    ("#6B6B6B", "#B0B0B0", 0.9, 0.9),
    "galena":          ("#3A3C4A", "#7A7E91", 0.9, 1.2),
    "halite":          ("#D4C4A1", "#F9F1DC", 1.0, 0.9),
    "sphalerite":      ("#5E4935", "#A68B6A", 0.9, 1.1),
    "lepidolite":      ("#A36BA3", "#F0B2F0", 1.1, 1.2),

    # Новые металлы
    "lead":            ("#3D4259", "#7D87B0", 0.9, 1.1), # Свинец: синевато-серый, тусклый
    "uranium":         ("#3E523A", "#9DFF00", 1.1, 1.4), # Уран: темно-зеленый с кислотным бликом
    "platinum":        ("#CFE2E6", "#FFFFFF", 1.2, 1.3), # Платина: холодный бело-голубой блеск
    "silver":          ("#A5BCC2", "#F0F8FA", 1.1, 1.5), # Серебро: очень яркий, чистый отблеск
    "steel":           ("#525252", "#A1A1A1", 1.0, 1.2), # Сталь: классический нейтральный металл
    
    # Дополнительные индустриальные материалы
    "titanium":        ("#6D6375", "#D9D0E3", 1.1, 1.2), # Титан: легкий фиолетово-серый оттенок
    "bronze":          ("#824F21", "#EBB067", 1.0, 1.3), # Бронза: темнее латуни, красноватый отблеск
    "invar":           ("#4A524A", "#9BA39B", 1.0, 1.1), # Инвар: серо-зеленый сплав (никель+железо)
    "electrum":        ("#B5A632", "#FFF9C4", 1.2, 1.4), # Электрум: сплав золота и серебра, очень яркий
    "cupronickel":     ("#8C9E91", "#D1E0D6", 1.0, 1.1), # Мельхиор: бледно-серебристый с теплым отливом
    "kanthal":         ("#5E4033", "#B38C7D", 1.0, 1.2), # Кантал: сплав для нагревателей, коричнево-стальной
    "nichrome":        ("#4D4D5C", "#9494B0", 1.1, 1.1), # Нихром: серо-фиолетовый блеск
}

VARIATIONS = [
    "raw_%", "%_ingot", "%_dust", "%_plate", "%_mote", 
    "%_nugget", "%_reductor", "%_cog", "%_wire", 
    "%_coil", "%_rod", "%_ore", "%_block", "raw_%_block"
]

def hex_to_rgb(hex_str):
    hex_str = hex_str.lstrip('#')
    return tuple(int(hex_str[i:i+2], 16) for i in (0, 2, 4))

def create_metal_gradient(size, color_base, color_highlight):
    """Создает диагональный градиент между двумя заданными цветами отблеска."""
    rgb_base = hex_to_rgb(color_base)
    rgb_high = hex_to_rgb(color_highlight)
    
    gradient = Image.new('RGBA', size)
    draw = ImageDraw.Draw(gradient)
    
    # Расстояние для градиента (диагональ)
    max_dist = size[0] + size[1]
    
    for i in range(max_dist):
        # ratio 0.0 (верх-лево, блик) -> 1.0 (низ-право, база)
        ratio = i / max_dist
        curr_rgb = tuple(int(rgb_high[j] * (1 - ratio) + rgb_base[j] * ratio) for j in range(3))
        # Рисуем диагональные линии
        draw.line([(i, 0), (0, i)], fill=curr_rgb + (255,))
    
    return gradient

def process_textures():
    if not os.path.exists(OUTPUT_DIR): os.makedirs(OUTPUT_DIR)
    if not os.path.exists(BLOCK_OUTPUT_DIR): os.makedirs(BLOCK_OUTPUT_DIR)

    for mat_name, (c_base, c_high, brightness, contrast) in MATERIALS.items():
        for pattern in VARIATIONS:
            file_name = pattern.replace("%", mat_name) + ".png"
            is_block = any(x in pattern for x in ["ore", "_block"])
            save_path = os.path.join(BLOCK_OUTPUT_DIR if is_block else OUTPUT_DIR, file_name)

            if os.path.exists(save_path):
                continue

            template_suffix = pattern.replace("%", "default")
            template_path = os.path.join(TEMPLATE_DIR, f"{template_suffix}.png")

            if not os.path.exists(template_path):
                continue

            try:
                with Image.open(template_path).convert("RGBA") as img:
                    # 1. Генерируем градиент от блика к базе
                    tint_layer = create_metal_gradient(img.size, c_base, c_high)
                    
                    # 2. Накладываем тинт (Multiply хорошо сохраняет тени исходника)
                    result = ImageChops.multiply(img, tint_layer)
                    
                    # 3. Применяем эффекты блеска (контрастность вытягивает границы между цветами)
                    if brightness != 1.0:
                        result = ImageEnhance.Brightness(result).enhance(brightness)
                    if contrast != 1.0:
                        result = ImageEnhance.Contrast(result).enhance(contrast)
                    
                    # 4. Маскируем по альфа-каналу шаблона
                    result.putalpha(img.getchannel('A'))
                    
                    result.save(save_path)
                    print(f"[+] {mat_name.upper()} -> {file_name}")
            except Exception as e:
                print(f"[X] Error {file_name}: {e}")

if __name__ == "__main__":
    process_textures()