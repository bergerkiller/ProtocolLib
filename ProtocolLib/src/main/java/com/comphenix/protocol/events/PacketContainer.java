/*
 *  ProtocolLib - Bukkit server library that allows access to the Minecraft protocol.
 *  Copyright (C) 2012 Kristian S. Stangeland
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the 
 *  GNU General Public License as published by the Free Software Foundation; either version 2 of 
 *  the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; 
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 *  See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with this program; 
 *  if not, write to the Free Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 
 *  02111-1307 USA
 */

package com.comphenix.protocol.events;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.bukkit.World;
import org.bukkit.WorldType;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.comphenix.protocol.injector.StructureCache;
import com.comphenix.protocol.reflect.EquivalentConverter;
import com.comphenix.protocol.reflect.FuzzyReflection;
import com.comphenix.protocol.reflect.StructureModifier;

import net.minecraft.server.Packet;

/**
 * Represents a Minecraft packet indirectly.
 * 
 * @author Kristian
 */
public class PacketContainer implements Serializable {

	/**
	 * Generated by Eclipse.
	 */
	private static final long serialVersionUID = 2074805748222377230L;
	
	protected int id;
	protected transient Packet handle;

	// Current structure modifier
	protected transient StructureModifier<Object> structureModifier;
		
	// Check whether or not certain classes exists
	private static boolean hasWorldType = false;
	
	// The getEntity method
	private static Method getEntity;
	
	// Support for serialization
	private static Method writeMethod;
	private static Method readMethod;
	
	static {
		try {
			Class.forName("net.minecraft.server.WorldType");
			hasWorldType = true;
		} catch (ClassNotFoundException e) {
		}
	}
	
	/**
	 * Creates a packet container for a new packet.
	 * @param id - ID of the packet to create.
	 */
	public PacketContainer(int id) {
		this(id, StructureCache.newPacket(id));
	}
	
	/**
	 * Creates a packet container for an existing packet.
	 * @param id - ID of the given packet.
	 * @param handle - contained packet.
	 */
	public PacketContainer(int id, Packet handle) {
		this(id, handle, StructureCache.getStructure(id).withTarget(handle));
	}
	
	/**
	 * Creates a packet container for an existing packet.
	 * @param id - ID of the given packet.
	 * @param handle - contained packet.
	 * @param structure - structure modifier.
	 */
	public PacketContainer(int id, Packet handle, StructureModifier<Object> structure) {
		if (handle == null)
			throw new IllegalArgumentException("handle cannot be null.");
		
		this.id = id;
		this.handle = handle;
		this.structureModifier = structure;
	}
	
	/**
	 * Retrieves the underlying Minecraft packet. 
	 * @return Underlying Minecraft packet.
	 */
	public Packet getHandle() {
		return handle;
	}
	
	/**
	 * Retrieves the generic structure modifier for this packet.
	 * @return Structure modifier.
	 */
	public StructureModifier<Object> getModifier() {
		return structureModifier;
	}
	
	/**
	 * Retrieves a read/write structure for every field with the given type.
	 * @param primitiveType - the type to find.
	 * @return A modifier for this specific type.
	 */
	public <T> StructureModifier<T> getSpecificModifier(Class<T> primitiveType) {
		return structureModifier.withType(primitiveType);
	}
	
	/**
	 * Retrieves a read/write structure for ItemStack.
	 * <p>
	 * This modifier will automatically marshall between the Bukkit ItemStack and the
	 * internal Minecraft ItemStack.
	 * @return A modifier for ItemStack fields.
	 */
	public StructureModifier<ItemStack> getItemModifier() {
		// Convert from and to the Bukkit wrapper
		return structureModifier.<ItemStack>withType(net.minecraft.server.ItemStack.class, 
				getIgnoreNull(new EquivalentConverter<ItemStack>() {
			public Object getGeneric(ItemStack specific) {
				return toStackNMS(specific);
			}
			
			@Override
			public ItemStack getSpecific(Object generic) {
				return new CraftItemStack((net.minecraft.server.ItemStack) generic);
			}
			
			@Override
			public Class<ItemStack> getSpecificType() {
				return ItemStack.class;
			}
		}));
	}
	
	/**
	 * Retrieves a read/write structure for arrays of ItemStacks.
	 * <p>
	 * This modifier will automatically marshall between the Bukkit ItemStack and the
	 * internal Minecraft ItemStack.
	 * @return A modifier for ItemStack array fields.
	 */
	public StructureModifier<ItemStack[]> getItemArrayModifier() {
		// Convert to and from the Bukkit wrapper
		return structureModifier.<ItemStack[]>withType(
				net.minecraft.server.ItemStack[].class, 
				getIgnoreNull(new EquivalentConverter<ItemStack[]>() {
					
			public Object getGeneric(ItemStack[] specific) {
				net.minecraft.server.ItemStack[] result = new net.minecraft.server.ItemStack[specific.length];
				
				// Unwrap every item
				for (int i = 0; i < result.length; i++) {
					result[i] = toStackNMS(specific[i]);
				}
				return result;
			}
			
			@Override
			public ItemStack[] getSpecific(Object generic) {
				net.minecraft.server.ItemStack[] input = (net.minecraft.server.ItemStack[]) generic;
				ItemStack[] result = new ItemStack[input.length];
				
				// Add the wrapper
				for (int i = 0; i < result.length; i++) {
					result[i] = new CraftItemStack(input[i]);
				}
				return result;
			}
			
			@Override
			public Class<ItemStack[]> getSpecificType() {
				return ItemStack[].class;
			}
		}));
	}
	
	/**
	 * Convert an item stack to the NMS equivalent.
	 * @param stack - Bukkit stack to convert.
	 * @return A bukkit stack.
	 */
	private net.minecraft.server.ItemStack toStackNMS(ItemStack stack) {
		// We must be prepared for an object that simply implements ItemStcak
		if (stack instanceof CraftItemStack) {
			return ((CraftItemStack) stack).getHandle();
		} else {
			return (new CraftItemStack(stack)).getHandle();
		}
	}
	
	/**
	 * Retrieves a read/write structure for the world type enum.
	 * <p>
	 * This modifier will automatically marshall between the Bukkit world type and the
	 * internal Minecraft world type.
	 * @return A modifier for world type fields.
	 */
	public StructureModifier<WorldType> getWorldTypeModifier() {
	
		if (!hasWorldType) {
			// We couldn't find the Minecraft equivalent
			return structureModifier.withType(null);
		}
		
		// Convert to and from the Bukkit wrapper
		return structureModifier.<WorldType>withType(
				net.minecraft.server.WorldType.class, 
				getIgnoreNull(new EquivalentConverter<WorldType>() {
					
			@Override
			public Object getGeneric(WorldType specific) {
				return net.minecraft.server.WorldType.getType(specific.getName());
			}
			
			@Override
			public WorldType getSpecific(Object generic) {
				net.minecraft.server.WorldType type = (net.minecraft.server.WorldType) generic;
				return WorldType.getByName(type.name());
			}
			
			@Override
			public Class<WorldType> getSpecificType() {
				return WorldType.class;
			}
		}));
	}
	
	/**
	 * Retrieves a read/write structure for entity objects.
	 * <p>
	 * Note that entities are transmitted by integer ID, and the type may not be enough
	 * to distinguish between entities and other values. Thus, this structure modifier
	 * MAY return null or invalid entities for certain fields. Using the correct index 
	 * is essential.
	 * 
	 * @return A modifier entity types.
	 */
	public StructureModifier<Entity> getEntityModifier(World world) {
	
		final Object worldServer = ((CraftWorld) world).getHandle();
		final Class<?> nmsEntityClass = net.minecraft.server.Entity.class;
		final World worldCopy = world;
		
		if (getEntity == null)
			getEntity = FuzzyReflection.fromObject(worldServer).getMethodByParameters(
					"getEntity", nmsEntityClass, new Class[] { int.class });
		
		// Convert to and from the Bukkit wrapper
		return structureModifier.<Entity>withType(
				int.class, 
				getIgnoreNull(new EquivalentConverter<Entity>() {
					
			@Override
			public Object getGeneric(Entity specific) {
				// Simple enough
				return specific.getEntityId();
			}
			
			@Override
			public Entity getSpecific(Object generic) {
				try {
					net.minecraft.server.Entity nmsEntity = (net.minecraft.server.Entity) 
							getEntity.invoke(worldServer, generic);
					Integer id = (Integer) generic;
					
					// Attempt to get the Bukkit entity
					if (nmsEntity != null) {
						return nmsEntity.getBukkitEntity();
					} else {
						// Maybe it's a player that's just logged in? Try a search
						for (Player player : worldCopy.getPlayers()) {
							if (player.getEntityId() == id) {
								return player;
							}
						}
						
						System.out.println("Entity doesn't exist.");
						return null;
					}
					
				} catch (IllegalArgumentException e) {
					throw new RuntimeException("Incorrect arguments detected.", e);
				} catch (IllegalAccessException e) {
					throw new RuntimeException("Cannot read field due to a security limitation.", e);
				} catch (InvocationTargetException e) {
					throw new RuntimeException("Error occured in Minecraft method.", e.getCause());
				}
			}
			
			@Override
			public Class<Entity> getSpecificType() {
				return Entity.class;
			}
		}));
	}
	
	private <TType> EquivalentConverter<TType> getIgnoreNull(final EquivalentConverter<TType> delegate) {
		// Automatically wrap all parameters to the delegate with a NULL check
		return new EquivalentConverter<TType>() {
			public Object getGeneric(TType specific) {
				if (specific != null)
					return delegate.getGeneric(specific);
				else
					return null;
			}
			
			@Override
			public TType getSpecific(Object generic) {
				if (generic != null)
					return delegate.getSpecific(generic);
				else
					return null;
			}
			
			@Override
			public Class<TType> getSpecificType() {
				return delegate.getSpecificType();
			}
		};
	}

	/**
	 * Retrieves the ID of this packet.
	 * @return Packet ID.
	 */
	public int getID() {
		return id;
	}
	
	private void writeObject(ObjectOutputStream output) throws IOException {
	    // Default serialization 
		output.defaultWriteObject();

		// We'll take care of NULL packets as well
		output.writeBoolean(handle != null);
		
		// Retrieve the write method by reflection
		if (writeMethod == null)
			writeMethod = FuzzyReflection.fromObject(handle).getMethodByParameters("write", DataOutputStream.class);
		
		try {
			// Call the write-method
			writeMethod.invoke(handle, new DataOutputStream(output));
		} catch (IllegalArgumentException e) {
			throw new IOException("Minecraft packet doesn't support DataOutputStream", e);
		} catch (IllegalAccessException e) {
			throw new RuntimeException("Insufficient security privileges.", e);
		} catch (InvocationTargetException e) {
			throw new IOException("Could not serialize Minecraft packet.", e);
		}
	}

	private void readObject(ObjectInputStream input) throws ClassNotFoundException, IOException {
	    // Default deserialization
		input.defaultReadObject();
		
		// Get structure modifier
		structureModifier = StructureCache.getStructure(id);

	    // Don't read NULL packets
	    if (input.readBoolean()) {
	    	
	    	// Create a default instance of the packet
	    	handle = StructureCache.newPacket(id);
	    	
			// Retrieve the read method by reflection
			if (readMethod == null)
				readMethod = FuzzyReflection.fromObject(handle).getMethodByParameters("read", DataInputStream.class);
	    	
			// Call the read method
			try {
				readMethod.invoke(handle, new DataInputStream(input));
			} catch (IllegalArgumentException e) {
				throw new IOException("Minecraft packet doesn't support DataInputStream", e);
			} catch (IllegalAccessException e) {
				throw new RuntimeException("Insufficient security privileges.", e);
			} catch (InvocationTargetException e) {
				throw new IOException("Could not deserialize Minecraft packet.", e);
			}
			
			// And we're done
			structureModifier = structureModifier.withTarget(handle);
	    }
	}
}
