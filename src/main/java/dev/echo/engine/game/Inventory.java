package dev.echo.engine.game;

import dev.echo.engine.api.ItemDefinition;
import dev.echo.engine.api.ResourceId;
import dev.echo.engine.runtime.registry.RuntimeRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Inventory {
    public static final int SLOT_COUNT=36;public static final int HOTBAR_SIZE=9;private final ArrayList<ItemStack> slots=new ArrayList<>(SLOT_COUNT);private final RuntimeRegistry<ItemDefinition> items;private int selected;
    public Inventory(RuntimeRegistry<ItemDefinition> items){this.items=items;for(int i=0;i<SLOT_COUNT;i++)slots.add(ItemStack.empty());}
    public int selected(){return selected;}public void select(int index){selected=Math.floorMod(index,HOTBAR_SIZE);}public ItemStack selectedStack(){return slots.get(selected);}public ItemStack slot(int index){return slots.get(index);}public List<ItemStack> snapshot(){return slots.stream().map(ItemStack::copy).toList();}
    public int count(ResourceId id){return slots.stream().filter(s->!s.emptyStack()&&s.itemId().equals(id)).mapToInt(ItemStack::count).sum();}
    public int add(ResourceId id,int amount){if(amount<=0)return 0;ItemDefinition definition=items.find(id).orElse(null);if(definition==null)return amount;int remaining=amount;for(ItemStack stack:slots){if(remaining==0)break;if(!stack.emptyStack()&&stack.itemId().equals(id))remaining-=stack.add(remaining,definition.maxStack());}for(ItemStack stack:slots){if(remaining==0)break;if(stack.emptyStack()){int accepted=Math.min(remaining,definition.maxStack());stack.set(id,accepted);remaining-=accepted;}}return remaining;}
    public boolean consume(ResourceId id,int amount){if(amount<=0)return true;if(count(id)<amount)return false;int remaining=amount;for(int i=slots.size()-1;i>=0&&remaining>0;i--){ItemStack stack=slots.get(i);if(!stack.emptyStack()&&stack.itemId().equals(id))remaining-=stack.remove(remaining);}return true;}
    public boolean consumeSelected(int amount){ItemStack stack=selectedStack();return !stack.emptyStack()&&stack.remove(amount)==amount;}
    public List<Map<String,Object>> toJson(){ArrayList<Map<String,Object>> out=new ArrayList<>();for(int i=0;i<slots.size();i++){ItemStack stack=slots.get(i);if(stack.emptyStack())continue;LinkedHashMap<String,Object> row=new LinkedHashMap<>();row.put("slot",i);row.put("item",stack.itemId().toString());row.put("count",stack.count());out.add(row);}return out;}
    public void restore(List<?> rows){for(ItemStack slot:slots)slot.set(null,0);if(rows==null)return;for(Object value:rows){if(!(value instanceof Map<?,?> raw))continue;Map<String,Object> row=new LinkedHashMap<>();raw.forEach((k,v)->row.put(String.valueOf(k),v));int slot=row.get("slot") instanceof Number n?n.intValue():-1;int count=row.get("count") instanceof Number n?n.intValue():0;String id=String.valueOf(row.getOrDefault("item",""));if(slot>=0&&slot<slots.size()&&count>0&&!id.isBlank()&&items.find(ResourceId.parse(id)).isPresent())slots.get(slot).set(ResourceId.parse(id),count);}}
}
