package ru.reweu.game.render;

/**
 * Границы каскадов теней по расстоянию от камеры (мир): смесь log/uniform (как в типичном CSM).
 */
public final class ShadowCascadeSplits {

    private ShadowCascadeSplits() {
    }

    /**
     * @param cascadeCount число каскадов ({@code >= 1})
     * @param near         ближняя плоскость камеры
     * @param far          дальняя плоскость камеры
     * @param lambda       0 = полностью uniform, 1 = полностью logarithmic
     * @param outSplits    длина {@code cascadeCount - 1}: граница между каскадами (последний каскад до {@code far})
     */
    public static void computeSplitDistances(int cascadeCount, float near, float far, float lambda, float[] outSplits) {
        if (cascadeCount <= 1 || outSplits == null) {
            return;
        }
        int n = cascadeCount - 1;
        if (outSplits.length < n) {
            throw new IllegalArgumentException("outSplits.length < cascadeCount - 1");
        }
        float range = far - near;
        for (int i = 0; i < n; i++) {
            float p = (i + 1) / (float) cascadeCount;
            float logZ = near * (float) Math.pow(far / near, p);
            float uniZ = near + range * p;
            outSplits[i] = lambda * logZ + (1f - lambda) * uniZ;
        }
    }
}
