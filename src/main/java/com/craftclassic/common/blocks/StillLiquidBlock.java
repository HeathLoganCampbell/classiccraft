package com.craftclassic.common.blocks;

public class StillLiquidBlock extends Block 
{
	public StillLiquidBlock(int id, String name, int textureId) 
	{
		super(id, name, textureId, false);
		this.setTicking(true);
		this.setLiquid(true);
	}
	
	//scheduleLater(() -> {Tasks}, ticks)
	//scheduleRepeat(() -> {task}, ticks)
}
