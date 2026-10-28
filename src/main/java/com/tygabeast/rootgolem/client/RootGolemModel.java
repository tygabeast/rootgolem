package com.tygabeast.rootgolem.client;

import com.tygabeast.rootgolem.RootGolemMod;
import com.tygabeast.rootgolem.entity.RootGolem;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Iron-golem-sized treant made of rooted dirt: same body proportions and walk as the iron golem,
 * plus a mossy crown with a sapling, moss pads on the shoulders, root fingers and spreading root toes.
 * All parts are baked once at startup.
 */
public class RootGolemModel extends HierarchicalModel<RootGolem> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(RootGolemMod.id("root_golem"), "main");

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart sprout;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public RootGolemModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.sprout = head.getChild("sprout");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4.0F, -12.0F, -5.5F, 8, 10, 8)
                        .texOffs(24, 0).addBox(-1.0F, -5.0F, -7.5F, 2, 4, 2)            // nose, as on the iron golem
                        .texOffs(84, 0).addBox(-4.5F, -12.5F, -6.0F, 9, 2, 9),          // moss crown
                PartPose.offset(0.0F, -7.0F, -2.0F));
        PartDefinition sprout = head.addOrReplaceChild("sprout", CubeListBuilder.create()
                        .texOffs(84, 12).addBox(-0.5F, -3.0F, -0.5F, 1, 3, 1),
                PartPose.offset(1.5F, -12.5F, -1.5F));
        sprout.addOrReplaceChild("leaf_a", CubeListBuilder.create().texOffs(90, 12)
                .addBox(0.0F, -1.0F, -1.0F, 3, 1, 2), PartPose.offsetAndRotation(0.5F, -2.5F, 0.0F, 0.0F, 0.0F, -0.5F));
        sprout.addOrReplaceChild("leaf_b", CubeListBuilder.create().texOffs(100, 12)
                .addBox(-3.0F, -1.0F, -1.0F, 3, 1, 2), PartPose.offsetAndRotation(-0.5F, -2.0F, 0.0F, 0.0F, 0.0F, 0.5F));
        head.addOrReplaceChild("twig", CubeListBuilder.create().texOffs(110, 12)
                .addBox(-0.5F, -0.5F, -4.0F, 1, 1, 4), PartPose.offsetAndRotation(-3.0F, -11.0F, 0.0F, -0.9F, 0.5F, 0.0F));

        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 40).addBox(-9.0F, -2.0F, -6.0F, 18, 12, 11)
                        .texOffs(0, 70).addBox(-4.5F, 10.0F, -3.0F, 9, 5, 6, new CubeDeformation(0.5F))
                        .texOffs(82, 21).addBox(-10.0F, -4.0F, -4.0F, 6, 3, 7)          // moss pad, right shoulder
                        .texOffs(82, 31).addBox(4.0F, -4.0F, -4.0F, 6, 3, 7),           // moss pad, left shoulder
                PartPose.offset(0.0F, -7.0F, 0.0F));
        // A few roots hanging from the chest.
        body.addOrReplaceChild("hanging_roots", CubeListBuilder.create()
                        .texOffs(82, 54).addBox(-6.0F, 10.0F, -6.2F, 1, 3, 1)
                        .texOffs(82, 54).addBox(-2.0F, 10.0F, -6.2F, 1, 2, 1)
                        .texOffs(82, 54).addBox(5.0F, 10.0F, -6.2F, 1, 3, 1),
                PartPose.ZERO);

        PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(60, 21).addBox(-13.0F, -2.5F, -3.0F, 4, 30, 6), PartPose.offset(0.0F, -7.0F, 0.0F));
        PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(60, 58).addBox(9.0F, -2.5F, -3.0F, 4, 30, 6), PartPose.offset(0.0F, -7.0F, 0.0F));
        addFingers(rightArm, -11.0F);
        addFingers(leftArm, 11.0F);

        PartDefinition rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(37, 0).addBox(-3.5F, -3.0F, -3.0F, 6, 16, 5), PartPose.offset(-4.0F, 11.0F, 0.0F));
        PartDefinition leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(60, 0).mirror().addBox(-3.5F, -3.0F, -3.0F, 6, 16, 5), PartPose.offset(5.0F, 11.0F, 0.0F));
        addToes(rightLeg);
        addToes(leftLeg);

        return LayerDefinition.create(mesh, 128, 128);
    }

    private static void addFingers(PartDefinition arm, float x) {
        arm.addOrReplaceChild("fingers", CubeListBuilder.create()
                        .texOffs(82, 42).addBox(-1.5F, 0.0F, -2.0F, 1, 4, 1)
                        .texOffs(86, 42).addBox(0.5F, 0.0F, -2.0F, 1, 5, 1)
                        .texOffs(90, 42).addBox(-0.5F, 0.0F, 1.0F, 1, 3, 1),
                PartPose.offset(x, 27.5F, 0.0F));
    }

    private static void addToes(PartDefinition leg) {
        leg.addOrReplaceChild("toe_a", CubeListBuilder.create().texOffs(82, 48)
                .addBox(-0.5F, -0.5F, -3.0F, 1, 1, 3), PartPose.offsetAndRotation(-2.0F, 12.5F, -3.0F, 0.0F, 0.35F, 0.0F));
        leg.addOrReplaceChild("toe_b", CubeListBuilder.create().texOffs(82, 48)
                .addBox(-0.5F, -0.5F, -3.0F, 1, 1, 3), PartPose.offsetAndRotation(1.5F, 12.5F, -3.0F, 0.0F, -0.35F, 0.0F));
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(RootGolem golem, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        head.xRot = headPitch * Mth.DEG_TO_RAD;

        float wave = Mth.triangleWave(limbSwing, 13.0F);
        rightLeg.xRot = -1.5F * wave * limbSwingAmount;
        leftLeg.xRot = 1.5F * wave * limbSwingAmount;
        rightArm.xRot = (-0.2F + 1.5F * wave) * limbSwingAmount;
        leftArm.xRot = (-0.2F - 1.5F * wave) * limbSwingAmount;

        // Planting: both arms reach down and forward (attackTime is the swing progress set by the renderer).
        if (attackTime > 0.0F) {
            float reach = Mth.sin(attackTime * Mth.PI);
            rightArm.xRot = -1.1F * reach;
            leftArm.xRot = -1.1F * reach;
        }

        // Slow sway of the sapling on its head.
        sprout.zRot = Mth.sin(ageInTicks * 0.07F) * 0.15F;
        sprout.xRot = Mth.cos(ageInTicks * 0.05F) * 0.08F + limbSwingAmount * 0.25F;
    }
}
