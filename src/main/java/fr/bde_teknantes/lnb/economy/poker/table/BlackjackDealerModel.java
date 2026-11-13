/*
 * From MineVegas Casino 1.1.1 by byMehd (https://www.curseforge.com/minecraft/mc-mods/minevegas-casino), under its
 * licence: MIT with an attribution condition, see LICENSES/MineVegas-Casino.txt. Decompiled, moved to this package,
 * otherwise unchanged unless a comment says so.
 */
package fr.bde_teknantes.lnb.economy.poker.table;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.Function;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

public final class BlackjackDealerModel extends Model {
   static final float FOREARM_SHRINK = 0.04F;
   private final ModelPart root;
   private final ModelPart torso;
   private final ModelPart head;
   private final ModelPart[] arms = new ModelPart[2];
   private final ModelPart[] forearms = new ModelPart[2];
   private final ModelPart[] legs = new ModelPart[2];
   private final DealerIdleAnimation.Pose pose = new DealerIdleAnimation.Pose();

   public BlackjackDealerModel(ModelPart root, Function<ResourceLocation, RenderType> renderType) {
      super(renderType);
      this.root = root;
      this.torso = root.getChild("torso");
      this.head = this.torso.getChild("head");
      this.arms[0] = this.torso.getChild("right_arm");
      this.arms[1] = this.torso.getChild("left_arm");
      this.forearms[0] = this.arms[0].getChild("right_forearm");
      this.forearms[1] = this.arms[1].getChild("left_forearm");
      this.legs[0] = root.getChild("right_leg");
      this.legs[1] = root.getChild("left_leg");
   }

   public void renderToBuffer(PoseStack pose, VertexConsumer vertices, int light, int overlay, int color) {
      this.root.render(pose, vertices, light, overlay, color);
   }

   public static LayerDefinition createLayer() {
      MeshDefinition mesh = new MeshDefinition();
      PartDefinition root = mesh.getRoot();
      PartDefinition torso = root.addOrReplaceChild("torso", CubeListBuilder.create(), PartPose.offset(0.0F, 12.0F, 0.0F));
      PartDefinition body = torso.addOrReplaceChild(
         "body", CubeListBuilder.create().texOffs(16, 20).addBox(-4.0F, -12.0F, -3.0F, 8.0F, 12.0F, 6.0F), PartPose.ZERO
      );
      body.addOrReplaceChild(
         "jacket", CubeListBuilder.create().texOffs(0, 38).addBox(-4.0F, -12.0F, -3.0F, 8.0F, 20.0F, 6.0F, new CubeDeformation(0.5F)), PartPose.ZERO
      );
      PartDefinition head = torso.addOrReplaceChild(
         "head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -10.0F, -4.0F, 8.0F, 10.0F, 8.0F), PartPose.offset(0.0F, -12.0F, 0.0F)
      );
      head.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(24, 0).addBox(-1.0F, -1.0F, -6.0F, 2.0F, 4.0F, 2.0F), PartPose.offset(0.0F, -2.0F, 0.0F));
      CubeDeformation forearm = new CubeDeformation(-0.04F, 0.0F, 0.0F);
      PartDefinition rightArm = torso.addOrReplaceChild(
         "right_arm", CubeListBuilder.create().texOffs(28, 46).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 6.0F, 4.0F), PartPose.offset(-6.0F, -10.0F, 0.0F)
      );
      rightArm.addOrReplaceChild(
         "right_forearm", CubeListBuilder.create().texOffs(44, 46).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, forearm), PartPose.offset(0.0F, 4.0F, 0.0F)
      );
      PartDefinition leftArm = torso.addOrReplaceChild(
         "left_arm", CubeListBuilder.create().texOffs(28, 46).mirror().addBox(-2.0F, -2.0F, -2.0F, 4.0F, 6.0F, 4.0F), PartPose.offset(6.0F, -10.0F, 0.0F)
      );
      leftArm.addOrReplaceChild(
         "left_forearm",
         CubeListBuilder.create().texOffs(44, 46).mirror().addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, forearm),
         PartPose.offset(0.0F, 4.0F, 0.0F)
      );
      root.addOrReplaceChild(
         "right_leg", CubeListBuilder.create().texOffs(0, 22).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F), PartPose.offset(-2.0F, 12.0F, 0.0F)
      );
      root.addOrReplaceChild(
         "left_leg", CubeListBuilder.create().texOffs(0, 22).mirror().addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F), PartPose.offset(2.0F, 12.0F, 0.0F)
      );
      return LayerDefinition.create(mesh, 64, 64);
   }

   public void setupAnim(BlackjackDealerModel.State state) {
      this.root.getAllParts().forEach(ModelPart::resetPose);
      DealerIdleAnimation.evaluate(state.ticks, this.pose);
      DealerPlayAnimation.apply(state.play, this.pose);
      this.torso.x = this.torso.x + this.pose.torsoX;
      this.torso.y = this.torso.y + this.pose.torsoY;
      this.torso.xRot = this.pose.torsoXRot;
      this.torso.yRot = this.pose.torsoYRot;
      this.torso.zRot = this.pose.torsoZRot;
      this.head.xRot = this.pose.headXRot;
      this.head.yRot = this.pose.headYRot;
      this.head.zRot = this.pose.headZRot;

      for (int i = 0; i < 2; i++) {
         this.arms[i].xRot = this.pose.shoulderXRot[i];
         this.arms[i].yRot = this.pose.shoulderYRot[i];
         this.arms[i].zRot = this.pose.shoulderZRot[i];
         this.forearms[i].xRot = this.pose.elbowXRot[i];
         this.legs[i].x = this.legs[i].x + this.pose.legX[i];
         this.legs[i].zRot = this.pose.legZRot[i];
      }
   }

   public static final class State {
      public float ticks;
      public final DealerPlayAnimation.Input play = new DealerPlayAnimation.Input();
   }
}
