/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.core.ml.online.neural;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FeedForwardNetworkTest {
    static final double[] HAND_WEIGHTS = {1,0,0, 0,1,0, 0,0,1, 1,2,3, 4,5,6, .1,.2,.3,.4};
    static final FeedForwardNetwork.Shape SHAPE = new FeedForwardNetwork.Shape(3,3,1,true,true);

    @Test void hand_calculation_exercises_all_connection_blocks_and_biases() {
        var network = new FeedForwardNetwork(SHAPE, NeuralActivation.LINEAR, NeuralActivation.LINEAR, HAND_WEIGHTS);
        assertEquals(19, SHAPE.parameterCount());
        assertEquals(16, new FeedForwardNetwork.Shape(3,3,1,false,true).parameterCount());
        assertEquals(15, new FeedForwardNetwork.Shape(3,3,1,true,false).parameterCount());
        assertEquals(11.1, network.predict(new double[]{2,-1,.5})[0], 1e-13);
        var eval = network.lossGradient(new double[][]{{2,-1,.5}}, new double[][]{{10}});
        assertEquals(1.21, eval.loss(), 1e-12);
        assertArrayEquals(new double[]{17.6,-8.8,4.4, 22,-11,5.5, 26.4,-13.2,6.6,
                4.4,-2.2,1.1, 4.62,-1.76,1.76, 8.8,11,13.2,2.2}, eval.gradient(), 1e-12);
    }

    @Test void loss_averages_rows_but_sums_output_coordinates() {
        var scalar = new FeedForwardNetwork(SHAPE, NeuralActivation.LINEAR, NeuralActivation.LINEAR, HAND_WEIGHTS);
        var rows = scalar.lossGradient(new double[][]{{2,-1,.5},{2,-1,.5}},new double[][]{{10},{12}});
        assertEquals(1.01,rows.loss(),1e-12);
        assertEquals(.2,rows.gradient()[18],1e-12);
        var shape=new FeedForwardNetwork.Shape(3,3,2,true,true);
        double[] weights={1,0,0,0,1,0,0,0,1, 1,2,3,4,5,6, 1,2,3,4,5,6, .1,.2,.3,.4,.4};
        var vector=new FeedForwardNetwork(shape,NeuralActivation.LINEAR,NeuralActivation.LINEAR,weights);
        assertEquals(2.42,vector.lossGradient(new double[][]{{2,-1,.5}},new double[][]{{10,10}}).loss(),1e-12);
    }

    @Test void finite_differences_check_every_parameter_for_all_activation_pairs_and_vector_outputs() {
        var shape=new FeedForwardNetwork.Shape(3,3,2,true,true);
        double[][] x={{.3,-.8,1.2},{-.6,.4,.2},{.5,.9,-.7}},y={{.2,-.3},{-.1,.4},{.5,.1}};
        double[] w=new double[shape.parameterCount()];
        for(int i=0;i<w.length;i++)w[i]=.2*Math.sin(.3*(i+1));
        for(var hidden:NeuralActivation.values())for(var output:NeuralActivation.values()) {
            var network=new FeedForwardNetwork(shape,hidden,output,w);
            double[] gradient=network.lossGradient(x,y).gradient();
            for(int i=0;i<w.length;i++) {
                double[] up=w.clone(),down=w.clone();up[i]+=1e-6;down[i]-=1e-6;
                double numerical=(new FeedForwardNetwork(shape,hidden,output,up).lossGradient(x,y).loss()
                        -new FeedForwardNetwork(shape,hidden,output,down).lossGradient(x,y).loss())/2e-6;
                assertEquals(numerical,gradient[i],2e-8,hidden+"/"+output+" parameter"+i);
            }
        }
    }

    @Test void shortcut_path_and_owned_parameters_survive_caller_mutations() {
        double[] w=new double[19];w[9]=2;w[10]=-3;w[11]=4;
        var network=new FeedForwardNetwork(SHAPE,NeuralActivation.TANH,NeuralActivation.LINEAR,w);
        w[9]=999;network.parameters()[9]=888;
        double[] output=network.predict(new double[]{1,2,3});assertEquals(8,output[0]);output[0]=999;
        assertEquals(8,network.predict(new double[]{1,2,3})[0]);
        assertSame(network,network.unwrap(FeedForwardNetwork.class).orElseThrow());
        var gradient=network.lossGradient(new double[][]{{1,2,3}},new double[][]{{7}});
        gradient.gradient()[9]=999;assertEquals(2,gradient.gradient()[9]);
    }

    @Test void rejects_invalid_dimensions_nonfinite_values_and_arithmetic_overflow() {
        assertThrows(IllegalArgumentException.class,()->new FeedForwardNetwork.Shape(0,3,1,true,true));
        assertThrows(IllegalArgumentException.class,()->new FeedForwardNetwork(SHAPE,NeuralActivation.LINEAR,NeuralActivation.LINEAR,new double[16]));
        var network=new FeedForwardNetwork(SHAPE,NeuralActivation.LINEAR,NeuralActivation.LINEAR,HAND_WEIGHTS);
        assertThrows(IllegalArgumentException.class,()->network.predict(new double[]{Double.NaN,1,2}));
        assertThrows(IllegalArgumentException.class,()->network.lossGradient(new double[][]{{1,2,3},{}},new double[][]{{1},{2}}));
        assertThrows(IllegalArgumentException.class,()->network.lossGradient(new double[0][],new double[0][]));
        assertThrows(IllegalArgumentException.class,()->network.lossGradient(new double[][]{{1,2,3}},new double[][]{{1},{2}}));
        assertThrows(ArithmeticException.class,()->network.predict(new double[]{Double.MAX_VALUE,Double.MAX_VALUE,Double.MAX_VALUE}));
    }
}
