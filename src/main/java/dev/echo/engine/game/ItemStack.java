package dev.echo.engine.game;

import dev.echo.engine.api.ResourceId;

public final class ItemStack {
    private ResourceId itemId;
    private int count;
    public ItemStack(ResourceId itemId,int count){this.itemId=itemId;this.count=Math.max(0,count);if(this.count==0)this.itemId=null;}
    public static ItemStack empty(){return new ItemStack(null,0);}
    public boolean emptyStack(){return itemId==null||count<=0;}public ResourceId itemId(){return itemId;}public int count(){return count;}
    public void set(ResourceId id,int value){itemId=id;count=Math.max(0,value);if(count==0)itemId=null;}
    public int add(int amount,int max){if(amount<=0)return 0;if(emptyStack())throw new IllegalStateException("Cannot add without assigning an item");int accepted=Math.min(amount,Math.max(0,max-count));count+=accepted;return accepted;}
    public int remove(int amount){int removed=Math.min(Math.max(0,amount),count);count-=removed;if(count==0)itemId=null;return removed;}
    public ItemStack copy(){return new ItemStack(itemId,count);}
}
