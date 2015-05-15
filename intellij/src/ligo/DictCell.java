package ligo;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Map;
import java.util.stream.Collectors;

public class DictCell implements Cell<Map<String, Object>> {
    private ArrayList<CellConsumer> consumers = new ArrayList<>();
    private Hashtable<String, SlotCell> slots = new Hashtable<>();

    @Override
    public Binding consume(CellConsumer<Map<String, Object>> consumer) {
        consumers.add(consumer);

        consumer.next(getVersion());

        return () -> consumers.remove(consumer);
    }

    private Map<String, Object> getVersion() {
        return slots.entrySet().stream().collect(Collectors.toMap(x -> x.getKey(), x -> x.getValue().value));
    }

    private class SlotCell implements Cell {
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
            if(DictCell.this.consumers.size() > 0) {
                Map<String, Object> dictVersion = getVersion();
                DictCell.this.consumers.forEach(x -> x.next(dictVersion));
            }
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
