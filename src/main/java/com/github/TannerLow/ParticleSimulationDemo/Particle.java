package com.github.TannerLow.ParticleSimulationDemo;

import java.util.List;

public class Particle {
    public static float gravitationalConstant;
    public static float deltaTime;
    public static List<Particle> particles;

    public int id;
    public float velocityX;
    public float velocityY;
    public float positionX;
    public float positionY;

    public Particle(int id, float positionX, float positionY, float velocityX, float velocityY) {
        this.id = id;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.positionX = positionX;
        this.positionY = positionY;
    }

    public void update() {
        float xAcceleration = 0;
        float yAcceleration = 0;

        for(Particle q : particles) {
            if(q.id != id) {
                float dx = Math.abs(q.positionX - positionX);
                float dy = Math.abs(q.positionY - positionY);
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                float xDirection = (q.positionX - positionX) > 0 ? 1.0f : -1.0f;
                float yDirection = (q.positionY - positionY) > 0 ? 1.0f : -1.0f;

                if(distance > 0.001f) {
                    float totalAcceleration = gravitationalConstant / (distance * distance); // assume all masses are 1kg
                    // cap acceleration
                    if(totalAcceleration > 1.0f) {
                        totalAcceleration = 1.0f;
                    }
                    // needed to avoid NaN due to float point errors that cause dx/distance > 1 which is invalid for acos
                    float adjOverHypot = dx / distance;
                    if(adjOverHypot > 1.0f) {
                        adjOverHypot = 1.0f;
                    }
                    float angle1 = (float) Math.acos(adjOverHypot);
                    xAcceleration += xDirection * Math.cos(angle1) * totalAcceleration;
                    yAcceleration += yDirection * Math.sin(angle1) * totalAcceleration;
                }
            }
        }

        velocityX += xAcceleration * deltaTime;
        velocityY += yAcceleration * deltaTime;

        positionX += velocityX * deltaTime;
        positionY += velocityY * deltaTime;

        if (positionX < 0.05f) {
            positionX = 0.05f;
            velocityX = 0;
        }
        if (positionX > 0.95f) {
            positionX = 0.95f;
            velocityX = 0;
        }

        if (positionY < 0.05f) {
            positionY = 0.05f;
            velocityY = 0;
        }
        if (positionY > 0.95f) {
            positionY = 0.95f;
            velocityY = 0;
        }
    }
}
