(function () {
    const canvas = document.getElementById('particles');
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    const COUNT = 55;
    const REPEL_RADIUS = 110;
    const REPEL_FORCE  = 2.8;

    let W, H;
    const mouse = { x: -9999, y: -9999 };

    function resize() {
        W = canvas.width  = window.innerWidth;
        H = canvas.height = window.innerHeight;
    }
    resize();
    window.addEventListener('resize', resize);

    window.addEventListener('mousemove', e => { mouse.x = e.clientX; mouse.y = e.clientY; });
    window.addEventListener('mouseleave', () => { mouse.x = -9999; mouse.y = -9999; });

    class Particle {
        constructor(randomY) { this.reset(randomY); }

        reset(randomY) {
            this.x      = Math.random() * W;
            this.y      = randomY ? Math.random() * H : H + 10;
            this.vx     = (Math.random() - 0.5) * 0.35;
            this.vy     = -(Math.random() * 0.45 + 0.15);
            this.baseVx = this.vx;
            this.baseVy = this.vy;
            this.r      = Math.random() * 1.4 + 0.4;
            this.alpha  = Math.random() * 0.22 + 0.05;
        }

        update() {
            const dx   = this.x - mouse.x;
            const dy   = this.y - mouse.y;
            const dist = Math.hypot(dx, dy);
            if (dist < REPEL_RADIUS && dist > 0) {
                const force = ((REPEL_RADIUS - dist) / REPEL_RADIUS) * REPEL_FORCE;
                this.vx += (dx / dist) * force;
                this.vy += (dy / dist) * force;
            }
            this.vx += (this.baseVx - this.vx) * 0.04;
            this.vy += (this.baseVy - this.vy) * 0.04;
            this.x += this.vx;
            this.y += this.vy;
            if (this.y < -10) this.reset(false);
            if (this.x < -10) this.x = W + 10;
            if (this.x > W + 10) this.x = -10;
        }

        draw() {
            ctx.save();
            ctx.globalAlpha = this.alpha;
            ctx.beginPath();
            ctx.arc(this.x, this.y, this.r, 0, Math.PI * 2);
            ctx.fillStyle = 'rgba(26,26,26,0.45)';
            ctx.fill();
            ctx.restore();
        }
    }

    const particles = Array.from({ length: COUNT }, () => new Particle(true));

    function loop() {
        ctx.clearRect(0, 0, W, H);
        particles.forEach(p => { p.update(); p.draw(); });
        requestAnimationFrame(loop);
    }
    loop();
})();
