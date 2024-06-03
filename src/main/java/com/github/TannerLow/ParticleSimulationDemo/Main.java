package com.github.TannerLow.ParticleSimulationDemo;

import org.jocl.*;

import javax.swing.*;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Main {
    private static final int NUM_PARTICLES = 500;
    private static final int WIDTH = 800;
    private static final int HEIGHT = 800;
    private static final float DELTA_TIME = 0.01f;
    private static final float GRAVITATIONAL_CONSTANT = 0.000000005f;
    private static final boolean printStatistics = true;
    private static final boolean useGPU = true;

    public static void main(String[] args) {
        if(useGPU) {
            calculateOnGPU();
        }
        else {
            calculateOnCPU();
        }
    }

    public static final String loadProgram() {
        String filePath = "Simulation.cl";
        try {
            String content = new String(Files.readAllBytes(Paths.get(filePath)));
            return content;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    public static void calculateOnGPU() {
        // Initialize OpenCL
        CL.setExceptionsEnabled(true);
        int platformIndex = 0;
        int deviceIndex = 0;
        long deviceType = CL.CL_DEVICE_TYPE_GPU;
        cl_platform_id[] platforms = new cl_platform_id[1];
        CL.clGetPlatformIDs(platforms.length, platforms, null);
        cl_platform_id platform = platforms[platformIndex];

        cl_device_id[] devices = new cl_device_id[1];
        CL.clGetDeviceIDs(platform, deviceType, devices.length, devices, null);
        cl_device_id device = devices[deviceIndex];

        String platformName = getStringInfo(platform, CL.CL_PLATFORM_NAME);
        String deviceName = getStringInfo(device, CL.CL_DEVICE_NAME);
        System.out.println("Platform: " + platformName);
        System.out.println("Device: " + deviceName);

        cl_context context = CL.clCreateContext(
                null, 1, new cl_device_id[]{device}, null, null, null);

        cl_command_queue commandQueue = CL.clCreateCommandQueueWithProperties(context, device, null, null);

        // Initialize particle data
        ByteBuffer particleBuffer = ByteBuffer.allocateDirect(NUM_PARTICLES * (2 * Float.BYTES + 2 * Float.BYTES)); // 2 floats for position, 2 floats for velocity
        particleBuffer.order(ByteOrder.nativeOrder());
        Random rand = new Random();
        for (int i = 0; i < NUM_PARTICLES; i++) {
            particleBuffer.putFloat(rand.nextFloat());  // x position
            particleBuffer.putFloat(rand.nextFloat());  // y position
            particleBuffer.putFloat((rand.nextFloat() - 0.5f) * 0.1f);  // x velocity
            particleBuffer.putFloat((rand.nextFloat() - 0.5f) * 0.1f);  // y velocity
        }
        particleBuffer.rewind();

        // Allocate memory on the device
        cl_mem particleMem = CL.clCreateBuffer(context, CL.CL_MEM_READ_WRITE | CL.CL_MEM_COPY_HOST_PTR,
                particleBuffer.capacity(), Pointer.to(particleBuffer), null);

        // Create and build the program
        cl_program program = CL.clCreateProgramWithSource(context, 1, new String[]{loadProgram()}, null, null);
        CL.clBuildProgram(program, 0, null, null, null, null);

        // Create the kernel
        cl_kernel kernel = CL.clCreateKernel(program, "updateParticles", null);

        // Set the kernel arguments
        CL.clSetKernelArg(kernel, 0, Sizeof.cl_mem, Pointer.to(particleMem));
        CL.clSetKernelArg(kernel, 1, Sizeof.cl_int, Pointer.to(new int[]{NUM_PARTICLES}));
        CL.clSetKernelArg(kernel, 2, Sizeof.cl_float, Pointer.to(new float[]{DELTA_TIME}));
        CL.clSetKernelArg(kernel, 3, Sizeof.cl_float, Pointer.to(new float[]{GRAVITATIONAL_CONSTANT}));

        // Set the work-item dimensions
        long global_work_size[] = new long[]{NUM_PARTICLES};

        // Setup the JFrame for rendering
        JFrame frame = new JFrame("Particle Simulation");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(WIDTH, HEIGHT);
        frame.setLocationRelativeTo(null);
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        JLabel label = new JLabel(new ImageIcon(image));
        frame.add(label);
        frame.setVisible(true);

        long totalTime = 0;
        int counter = 0;

        while (true) {
            long startTime = System.currentTimeMillis();

            // Execute the kernel
            CL.clEnqueueNDRangeKernel(commandQueue, kernel, 1, null,
                    global_work_size, null, 0, null, null);

            // Read the updated particle data
            CL.clEnqueueReadBuffer(commandQueue, particleMem, CL.CL_TRUE, 0,
                    particleBuffer.capacity(), Pointer.to(particleBuffer), 0, null, null);

            totalTime += System.currentTimeMillis() - startTime;
            counter++;
            if(counter == 100) {
                if(printStatistics) {
                    System.out.println("Average time per scene calculation: " + (totalTime / counter) + "ms");
                }
                totalTime = 0;
                counter = 0;
            }

            // Render the particles
            particleBuffer.rewind();
            Graphics2D g = image.createGraphics();
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, WIDTH, HEIGHT);
            g.setColor(Color.WHITE);

            for (int i = 0; i < NUM_PARTICLES; i++) {
                float x = particleBuffer.getFloat();
                float y = particleBuffer.getFloat();
                float vx = particleBuffer.getFloat();
                float vy = particleBuffer.getFloat();
                int drawX = (int) (x * WIDTH);
                int drawY = (int) ((1.0f - y) * HEIGHT);
                g.fillRect(drawX, drawY, 2, 2);
            }
            g.dispose();
            label.repaint();

            // Sleep to control the frame rate
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static void calculateOnCPU() {
        // Setup the JFrame for rendering
        JFrame frame = new JFrame("Particle Simulation");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(WIDTH, HEIGHT);
        frame.setLocationRelativeTo(null);
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        JLabel label = new JLabel(new ImageIcon(image));
        frame.add(label);
        frame.setVisible(true);

        List<Particle> particles = new ArrayList<>();
        Random rand = new Random();
        for (int i = 0; i < NUM_PARTICLES; i++) {
            Particle particle = new Particle(
                    i, // id
                    rand.nextFloat(), // x position
                    rand.nextFloat(), // y position
                    (rand.nextFloat() - 0.5f) * 0.1f, // x velocity
                    (rand.nextFloat() - 0.5f) * 0.1f // y velocity
            );
            particles.add(particle);
        }

        Particle.particles = particles;
        Particle.deltaTime = DELTA_TIME;
        Particle.gravitationalConstant = GRAVITATIONAL_CONSTANT;

        long totalTime = 0;
        int counter = 0;

        while (true) {
            long startTime = System.currentTimeMillis();

            for(Particle particle : particles) {
                particle.update();
            }

            totalTime += System.currentTimeMillis() - startTime;
            counter++;
            if(counter == 100) {
                if(printStatistics) {
                    System.out.println("Average time per scene calculation: " + (totalTime / counter) + "ms");
                }
                totalTime = 0;
                counter = 0;
            }

            // Render the particles
            Graphics2D g = image.createGraphics();
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, WIDTH, HEIGHT);
            g.setColor(Color.WHITE);

            for (Particle particle : particles) {
                int drawX = (int) (particle.positionX * WIDTH);
                int drawY = (int) ((1.0f - particle.positionY) * HEIGHT);
                g.fillRect(drawX, drawY, 2, 2);
            }
            g.dispose();
            label.repaint();

            // Sleep to control the frame rate
//            try {
//                Thread.sleep(10);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
        }
    }

    // Some helper methods for displaying GPU info

    private static String getStringInfo(cl_platform_id platform, int paramName) {
        long[] size = new long[1];
        CL.clGetPlatformInfo(platform, paramName, 0, null, size);
        byte[] buffer = new byte[(int) size[0]];
        CL.clGetPlatformInfo(platform, paramName, buffer.length, Pointer.to(buffer), null);
        return new String(buffer, 0, buffer.length - 1);
    }

    private static String getStringInfo(cl_device_id device, int paramName) {
        long[] size = new long[1];
        CL.clGetDeviceInfo(device, paramName, 0, null, size);
        byte[] buffer = new byte[(int) size[0]];
        CL.clGetDeviceInfo(device, paramName, buffer.length, Pointer.to(buffer), null);
        return new String(buffer, 0, buffer.length - 1);
    }
}
