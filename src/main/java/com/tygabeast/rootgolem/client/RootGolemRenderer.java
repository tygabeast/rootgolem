package com.tygabeast.rootgolem.client;

import com.tygabeast.rootgolem.RootGolemMod;
import com.tygabeast.rootgolem.entity.RootGolem;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

public class RootGolemRenderer extends MobRenderer<RootGolem, RootGolemModel> {
    private static final ResourceLocation TEXTURE = RootGolemMod.id("textures/entity/root_golem.png");
    private static final RenderType EYES = RenderType.eyes(RootGolemMod.id("textures/entity/root_golem_eyes.png"));

    public RootGolemRenderer(EntityRendererProvider.Context context) {
        super(context, new RootGolemModel(context.bakeLayer(RootGolemModel.LAYER)), 0.7F);
        // Glowing eyes: one extra draw of the same baked model, full-bright.
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return EYES;
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(RootGolem golem) {
        return TEXTURE;
    }
}
