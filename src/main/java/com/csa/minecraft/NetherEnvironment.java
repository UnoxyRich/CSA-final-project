package com.csa.minecraft;

import org.joml.Vector3f;

/**
 * Fixed nether atmosphere: constant warm-red glow, no daylight cycle,
 * no weather. All color methods return pre-baked nether values.
 */
public class NetherEnvironment extends Environment {

    @Override
    public Vector3f fogColor() {
        return new Vector3f(0.26f, 0.04f, 0.04f);
    }

    @Override
    public Vector3f skyTop() {
        return new Vector3f(0.05f, 0.01f, 0.01f);
    }

    @Override
    public Vector3f skyHorizon() {
        return new Vector3f(0.22f, 0.04f, 0.02f);
    }

    @Override
    public Vector3f ambient() {
        // Warm netherrack glow
        return new Vector3f(0.40f, 0.18f, 0.10f);
    }

    @Override
    public Vector3f sunColor() {
        return new Vector3f(0.95f, 0.42f, 0.14f);
    }

    @Override
    public float sunIntensity() {
        return 0.48f;
    }

    @Override
    public Vector3f sunDirection() {
        // Fixed overhead-ish direction (no moving sun in nether)
        return new Vector3f(0.30f, 0.80f, 0.22f).normalize();
    }

    @Override
    public float rainStrength()   { return 0f; }
    @Override
    public float thunderStrength(){ return 0f; }
    @Override
    public float wetness()        { return 0f; }
    @Override
    public float nightAmount()    { return 1f; }
    @Override
    public float sunDiscStrength(){ return 0f; }
    @Override
    public float moonDiscStrength(){ return 0f; }

    @Override
    public void update(float dt) {
        // No daylight cycle; still tick status messages via base class
        // (statusTimer is private, so we call super just for that)
        super.update(0f); // pass 0 so dayTime does not advance
    }
}
