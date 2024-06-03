typedef struct {
    float2 position;
    float2 velocity;
} Particle;

__kernel void updateParticles(
    __global Particle* particles,
    const int numParticles,
    const float deltaTime,
    const float gravitationalConstant)
{
    int gid = get_global_id(0);

    if (gid >= numParticles) {
        return;
    }

    Particle p = particles[gid];

    // sum up gravity relative to each other particle
    float xAcceleration = 0;
    float yAcceleration = 0;
    Particle q;
    for(int i = 0; i < numParticles; i++) {
        if(i != gid) {
            q = particles[i];
            float dx = fabs(q.position.x - p.position.x);
            float dy = fabs(q.position.y - p.position.y);
            float distance = sqrt(dx * dx + dy * dy);
            float xDirection = (q.position.x - p.position.x) > 0 ? 1.0f : -1.0f;
            float yDirection = (q.position.y - p.position.y) > 0 ? 1.0f : -1.0f;
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
                float angle1 = acos(adjOverHypot);
                xAcceleration += xDirection * cos(angle1) * totalAcceleration;
                yAcceleration += yDirection * sin(angle1) * totalAcceleration;
            }
        }
    }

    p.velocity.x += xAcceleration * deltaTime;
    p.velocity.y += yAcceleration * deltaTime;

    p.position += p.velocity * deltaTime;

    if (p.position.x < 0.05f) {
        p.position.x = 0.05f;
        p.velocity.x = 0;
    }
    if(p.position.x > 0.95f) {
        p.position.x = 0.95f;
        p.velocity.x = 0;
    }

    if (p.position.y < 0.05f) {
        p.position.y = 0.05f;
        p.velocity.y = 0;
    }
    if(p.position.y > 0.95f) {
        p.position.y = 0.95f;
        p.velocity.y = 0;
    }

    particles[gid] = p;
}

