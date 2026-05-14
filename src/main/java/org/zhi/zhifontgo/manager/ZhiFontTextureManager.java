package org.zhi.zhifontgo.manager;

import com.mojang.blaze3d.font.SheetGlyphInfo;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import javax.annotation.Nullable;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ZhiFontTextureManager extends AbstractTexture {
    private static final int SIZE = 2048;
    private final GlyphRenderTypes renderTypes;
    private final boolean colored;
    private final boolean irisCompatible;
    private final Node root;

    public ZhiFontTextureManager(GlyphRenderTypes renderTypes, boolean colored, boolean irisCompatible) {
        this.renderTypes = renderTypes;
        this.colored = colored;
        this.irisCompatible = irisCompatible;
        this.root = new Node(0, 0, SIZE, SIZE);
        TextureUtil.prepareImage(colored ? NativeImage.InternalGlFormat.RGBA : NativeImage.InternalGlFormat.RED, this.getId(), SIZE, SIZE);
        this.setFilter(!irisCompatible, false);
    }

    @Override
    public void load(ResourceManager resourceManager) {
    }

    @Nullable
    public BakedGlyph add(SheetGlyphInfo glyphInfo) {
        if (glyphInfo.isColored() != this.colored) {
            return null;
        }

        Node node = this.root.insert(glyphInfo);
        if (node == null) {
            return null;
        }

        this.bind();
        if (glyphInfo instanceof ZhiFontGlyphProviderManager.RuntimeSheetGlyphInfo runtimeSheetGlyphInfo) {
            runtimeSheetGlyphInfo.upload(node.x, node.y, this.irisCompatible);
        } else {
            glyphInfo.upload(node.x, node.y);
        }
        return new BakedGlyph(
            this.renderTypes,
            ((float)node.x + 0.01F) / SIZE,
            ((float)node.x - 0.01F + (float)glyphInfo.getPixelWidth()) / SIZE,
            ((float)node.y + 0.01F) / SIZE,
            ((float)node.y - 0.01F + (float)glyphInfo.getPixelHeight()) / SIZE,
            glyphInfo.getLeft(),
            glyphInfo.getRight(),
            glyphInfo.getTop(),
            glyphInfo.getBottom()
        );
    }

    @Override
    public void close() {
        this.releaseId();
    }

    private static final class Node {
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        @Nullable
        private Node left;
        @Nullable
        private Node right;
        private boolean occupied;

        private Node(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        @Nullable
        private Node insert(SheetGlyphInfo glyphInfo) {
            if (this.left != null && this.right != null) {
                Node result = this.left.insert(glyphInfo);
                return result != null ? result : this.right.insert(glyphInfo);
            }
            if (this.occupied) {
                return null;
            }

            int glyphWidth = glyphInfo.getPixelWidth();
            int glyphHeight = glyphInfo.getPixelHeight();
            if (glyphWidth > this.width || glyphHeight > this.height) {
                return null;
            }
            if (glyphWidth == this.width && glyphHeight == this.height) {
                this.occupied = true;
                return this;
            }

            int remainingWidth = this.width - glyphWidth;
            int remainingHeight = this.height - glyphHeight;
            if (remainingWidth > remainingHeight) {
                this.left = new Node(this.x, this.y, glyphWidth, this.height);
                this.right = new Node(this.x + glyphWidth + 1, this.y, this.width - glyphWidth - 1, this.height);
            } else {
                this.left = new Node(this.x, this.y, this.width, glyphHeight);
                this.right = new Node(this.x, this.y + glyphHeight + 1, this.width, this.height - glyphHeight - 1);
            }
            return this.left.insert(glyphInfo);
        }
    }
}
