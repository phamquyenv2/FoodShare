ALTER TABLE order_details DROP CHECK chk_order_details_amount;
ALTER TABLE order_details DROP COLUMN subtotal;
ALTER TABLE order_details ADD CONSTRAINT chk_order_details_unit_price CHECK (unit_price >= 0);
