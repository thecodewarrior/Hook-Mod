package com.thecodewarrior.hooks.proxy;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.entity.player.PlayerEvent;
import codechicken.lib.raytracer.RayTracer;
import codechicken.lib.vec.Vector3;

import com.thecodewarrior.hooks.util.ActiveHook;
import com.thecodewarrior.hooks.util.ExtendedVector3;
import com.thecodewarrior.hooks.util.HookProperties;
import com.thecodewarrior.hooks.util.HookUtil;
import com.thecodewarrior.hooks.util.HookWrapper;
import com.thecodewarrior.hooks.util.IResourceConfig;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class CommonProxy
{
	public static boolean isClient = false;

	public static boolean shouldPull = true;
	
	public void init(){}
	public void glAlign(Axis align, Vector3 along, Axis point, Axis to) {}
	public void glAlign(Vector3 align, Vector3 along, Vector3 point, Vector3 to) {}
//	Map<World, FakeEntityHook> eMap = new HashMap<World, FakeEntityHook>();
	public static boolean approxEqual(double a, double b){
	    return a == b ? true : Math.abs(a - b) < 0.0000001;
	}
	
	public static enum Axis {
		X(1,0,0), Y(0,1,0), Z(0,0,1);
		
		Vector3 vector;
		
		Axis(int x, int y, int z) {
			this.vector = new Vector3(x,y,z);
		}
		
		public Vector3 getVector()
		{
			return vector.copy();
		}
	}
	
	@SubscribeEvent
	public void onPlayerTick(TickEvent.PlayerTickEvent event) {
		HookProperties props = HookWrapper.getProperties(event.player);
		props.cleanHooks();
		props.isSteady = false;
		EntityPlayer player = event.player;
		Vector3 playerCenter = new Vector3(
				(player.boundingBox.minX+player.boundingBox.maxX)/2,
				(player.boundingBox.minY+player.boundingBox.maxY)/2,
				(player.boundingBox.minZ+player.boundingBox.maxZ)/2
				);
		Vector3 playerEyes = new Vector3( RayTracer.getCorrectedHeadVec(player) );
		
		
		World w = event.player.worldObj;
		
		List<ActiveHook> hooks = props.getHooks();
		
		Vector3 cumulitave = new Vector3();
		int cumulitaveCount = 0;
		int index = 0;
		for (ActiveHook hook : hooks)
		{
			hook.update(player);
			if(hook.isStopped() && !hook.isRetracting())
			{
				cumulitave.add(hook.getPullLocation());
				cumulitaveCount++;
				continue;
			}
			Vector3 loc = hook.getLocation();
			Vector3 vel = hook.getVelocity();
			Vector3 movement = vel;
			if(hook.isRetracting())
			{
				movement = vel = loc.copy().sub(playerCenter).normalize().multiply(-hook.getHook().getRetractSpeed());
				if(loc.copy().sub(playerCenter).mag() < hook.getHook().getRetractSpeed()) {
					hook.destroy();
				}
			} else {
				ExtendedVector3<ForgeDirection> hit = HookUtil.collisionRayCast(w, loc, vel, event.player);
				movement = hit;
				if(!(  approxEqual(movement.x, vel.x) && approxEqual(movement.y, vel.y) && approxEqual(movement.z, vel.z)  ) && !hook.isStopped())
				{
					hook.setHitSide(hit.getData());
					hook.setStopped();
				}
			}
			hook.setLocation(loc.copy().add( movement ));
//			if( !hook.isStopped() &&
//				distance(playerEyes, loc) > Math.pow(hook.getHook().getLength(), 2) )
//				hook.setRetracting();
			
			index++;
		}
			
		if(cumulitaveCount < 1)
			return;
		player.fallDistance = 0;
		
		Vector3 movement = cumulitave.multiply(1.0/cumulitaveCount);
		movement.sub(playerCenter);
		if(movement.mag() > 0.5)
			movement.normalize().multiply(0.5);
		if(approxEqual(player.posX - player.lastTickPosX, 0) &&
		   approxEqual(player.posY - player.lastTickPosY, 0) &&
		   approxEqual(player.posZ - player.lastTickPosZ, 0)) {
			props.isSteady = true;
		}
		if(shouldPull) {
			player.motionX = movement.x;
			player.motionY = movement.y;
			player.motionZ = movement.z;
		}
	}
	
	@SubscribeEvent
	public void digSpeed(PlayerEvent.BreakSpeed event)
	{
		if(!( event.entity instanceof EntityPlayer ))
			return;
		EntityPlayer p = (EntityPlayer) event.entity;
		HookProperties prop = HookWrapper.getProperties(p);
		if(!p.onGround && prop.isSteady)
			event.newSpeed = event.originalSpeed*5;
	}
	
	public double distance(Vector3 a, Vector3 b)
	{
		return MathHelper.sqrt_double(
				Math.pow(a.x-b.x, 2) +
				Math.pow(a.y-b.y, 2) +
				Math.pow(a.z-b.z, 2)
				);
	}

	public boolean isClientPlayer(Entity e)
	{
		return false;
	}
	
	public void registerResourceConfig(IResourceConfig obj) {}
	
}