package ligo;

import java.util.ArrayList;
import java.util.Hashtable;

public class DictCell implements Cell {
    private Hashtable<String, SlotCell> slots = new Hashtable<>();

    @Override
    public Binding consume(CellConsumer consumer) {
        return null;
    }

    private static class SlotCell implements Cell {
        private ArrayList<CellConsumer> consumers = new ArrayList<>();
        private Cell valueCell;
        private Binding valueCellBinding;
        private Object value;

        @Override
        public Binding consume(final CellConsumer consumer) {
            consumers.add(consumer);

            if(value != null)
                consumer.next(value);

            return () -> consumers.remove(consumer);
        }

        public void set(Cell valueCell) {
            if(valueCellBinding != null)
                valueCellBinding.remove();

            this.valueCell = valueCell;

            valueCellBinding = valueCell.consume(value -> {
                this.value = value;
                update();
            });
        }

        private void update() {
            consumers.forEach(x -> x.next(value));
        }

        public Cell getValueCell() {
            return valueCell;
        }
    }

    private SlotCell getSlot(String id) {
        SlotCell slot = slots.get(id);
        if(slot == null) {
            slot = new SlotCell();
            slots.put(id, slot);
        }
        return slot;
    }

    public void put(String id, Cell cellValue) {
        SlotCell slot = getSlot(id);
        slot.set(cellValue);
    }

    public Cell get(String id) {
        return getSlot(id);
    }

    public Cell getValueCell(String id) {
        return getSlot(id).getValueCell();
    }
}
