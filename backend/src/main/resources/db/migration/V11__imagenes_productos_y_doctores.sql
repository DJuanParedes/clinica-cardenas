-- Foto de cada material del inventario y foto de cada odontólogo (se guardan en la carpeta de archivos).
ALTER TABLE inventory_items ADD COLUMN image_path VARCHAR(120) NULL;
ALTER TABLE dentists ADD COLUMN photo_path VARCHAR(120) NULL;
