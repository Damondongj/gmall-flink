import os
import json
import pathlib
import logging
import traceback

from tensorflow import keras
from tensorflow import losses
from tensorflow import optimizers
from tensorflow.keras import layers
from tensorflow.keras import callbacks

from common.utils import emit, event_logger
from apps.star.utils.netural_tools import load_data
from apps.star.utils.netural_tools import parse_None
from apps.star.utils.netural_tools import parse_number
from apps.star.utils.netural_tools import clean_parameter

logger = logging.getLogger('edi_server')
os.environ["Path"] += os.pathsep + os.getcwd() + '\\Graphviz\\bin\\'
logger.info(os.getcwd() + '\\Graphviz\\bin\\')


class BuildModelLayers(object):

    def __init__(self):
        self.models = {}

    def build(self, data):
        """
            代码思路：
                通过写dfs得到了一条路径
                遍历该路径来进行创建模型,从一个input开始, 当一个模型由两个输入的时候,
                这时候会断开, 然后从另外一个input进入, 每一次创建一个层的时候通过一个字典来保存
                当前层的名字(layer_name)和layer_result
                当某一层有多个输入的时候,会进行判断,然后将这一层输入的的layer_name传到具体创建的里面
            参数:
                model_input: 模型输入
                model_output: 模型输出

                input_layers: 输入层的名称 list
                output_layers: 输出层的名称 list
                layers_info: c++ 穿过来的每一层具体的参数信息 {layer_name: {具体信息}, ...}
                in_out: 上一层 -> 下一层
                out_in: 下一层 -> 上一层
                layer_order: 通过dfs得到的一条遍历路径
        """
        model_input = []
        model_output = []

        input_layers = data['input_layers']
        layers_info = data['layers']
        output_layers = data['output_layers']
        in_out = data['in_out']
        out_in = data['out_in']
        layer_order = data['layer_order']

        for layer_name in layer_order:
            print(layer_name)
            input_info = layers_info[layer_name]
            if layer_name in input_layers:
                layer_result = self.input_layer(input_info['config'], layer_name)
                model_input.append(layer_result)
            else:
                layer_result = getattr(self, input_info['class_name'])(input_info['config'], out_in[layer_name], layer_name)
            self.models[layer_name] = layer_result

            if layer_name in output_layers:
                model_output.append(layer_result)

        model = keras.Model(
            inputs=model_input,
            outputs=model_output
        )
        return model

    def input_layer(self, layer_information, layer_name):

        shape = parse_number(layer_information, layer_name, 'shape')
        batch_size = parse_number(layer_information, layer_name, 'batch_size')
        dtype = parse_None(layer_information, 'dtype')
        tensor = parse_None(layer_information, 'tensor')
        sparse = parse_None(layer_information, 'sparse')
        ragged = parse_None(layer_information, 'ragged')
        type_spec = parse_None(layer_information, 'type_spec')
        name = layer_name

        layer_output = layers.Input(shape=shape,
                                     batch_size=batch_size,
                                     dtype=dtype,
                                     tensor=tensor,
                                     sparse=sparse,
                                     ragged=ragged,
                                     type_spec=type_spec,
                                     name=name)
        return layer_output

    def Conv2D(self, layer_information, input_name, layer_name):
        upper_layer = self.models[input_name[0]]

        filters = parse_number(layer_information, layer_name, 'filters')
        kernel_size = parse_number(layer_information, layer_name, 'kernel_size')
        strides = parse_number(layer_information, layer_name, 'strides')
        padding = parse_None(layer_information, 'padding')
        name = layer_name
        data_format = parse_None(layer_information, "data_format")
        groups = parse_number(layer_information, layer_name, 'groups')
        dilation_rate = parse_number(layer_information, layer_name, 'dilation_rate')
        activation = parse_None(layer_information, 'activation')
        use_bias = parse_None(layer_information, "use_bias")
        kernel_initializer = parse_None(layer_information, 'kernel_initializer')
        bias_initializer = parse_None(layer_information, 'bias_initializer')
        kernel_regularizer = parse_None(layer_information, "kernel_regularizer")
        bias_regularizer = parse_None(layer_information, "bias_regularizer")
        activity_regularizer = parse_None(layer_information, "activity_regularizer")
        kernel_constraint = parse_None(layer_information, "kernel_constraint")
        bias_constraint = parse_None(layer_information, "bias_constraint")

        layer_output = layers.Conv2D(filters=filters,
                                      kernel_size=kernel_size,
                                      strides=strides,
                                      padding=padding,
                                      name=name,
                                      data_format=data_format,
                                      groups=groups,
                                      dilation_rate=dilation_rate,
                                      activation=activation,
                                      use_bias=use_bias,
                                      kernel_initializer=kernel_initializer,
                                      bias_initializer=bias_initializer,
                                      kernel_regularizer=kernel_regularizer,
                                      bias_regularizer=bias_regularizer,
                                      activity_regularizer=activity_regularizer,
                                      kernel_constraint=kernel_constraint,
                                      bias_constraint=bias_constraint
                                      )(upper_layer)
        return layer_output


    def DepthwiseConv2D(self, layer_information, input_name, layer_name):
        upper_layer = self.models[input_name[0]]

        print(layer_information)
        kernel_size = parse_number(layer_information, layer_name, 'kernel_size')
        strides = parse_number(layer_information, layer_name, 'strides')
        padding = parse_None(layer_information, 'padding')
        depth_multiplier = parse_number(layer_information, layer_name, 'depth_multiplier')
        name = layer_name
        data_format = parse_None(layer_information, "data_format")
        dilation_rate = parse_number(layer_information, layer_name, 'dilation_rate')
        activation = parse_None(layer_information, 'activation')
        use_bias = parse_None(layer_information, "use_bias")
        depthwise_initializer = parse_None(layer_information, 'depthwise_initializer')
        bias_initializer = parse_None(layer_information, 'bias_initializer')
        depthwise_regularizer = parse_None(layer_information, "depthwise_regularizer")
        bias_regularizer = parse_None(layer_information, "bias_regularizer")
        activity_regularizer = parse_None(layer_information, "activity_regularizer")
        depthwise_constraint = parse_None(layer_information, "depthwise_constraint")
        bias_constraint = parse_None(layer_information, "bias_constraint")

        layer_output = layers.DepthwiseConv2D(kernel_size=kernel_size,
                                     strides=strides,
                                     padding=padding,
                                     name=name,
                                     data_format=data_format,
                                     depth_multiplier=depth_multiplier,
                                     dilation_rate=dilation_rate,
                                     activation=activation,
                                     use_bias=use_bias,
                                     depthwise_initializer=depthwise_initializer,
                                     bias_initializer=bias_initializer,
                                     depthwise_regularizer=depthwise_regularizer,
                                     bias_regularizer=bias_regularizer,
                                     activity_regularizer=activity_regularizer,
                                     depthwise_constraint=depthwise_constraint,
                                     bias_constraint=bias_constraint
                                     )(upper_layer)
        return layer_output


    def Dense(self, layer_information, input_name, layer_name):
        upper_layer = self.models[input_name[0]]

        units = parse_number(layer_information, layer_name, 'units')
        activation = parse_None(layer_information, "activation")
        name = layer_name
        use_bias = parse_None(layer_information, "use_bias")
        kernel_initializer = parse_None(layer_information, "kernel_initializer")
        bias_initializer = parse_None(layer_information, 'bias_initializer')
        kernel_regularizer = parse_None(layer_information, "kernel_regularizer")
        bias_regularizer = parse_None(layer_information, "bias_regularizer")
        activity_regularizer = parse_None(layer_information, "activity_regularizer")
        kernel_constraint = parse_None(layer_information, "kernel_constraint")
        bias_constraint = parse_None(layer_information, "bias_constraint")

        layer_output = layers.Dense(units,
                                     activation=activation,
                                     name=name,
                                     use_bias=use_bias,
                                     kernel_initializer=kernel_initializer,
                                     bias_initializer=bias_initializer,
                                     kernel_regularizer=kernel_regularizer,
                                     bias_regularizer=bias_regularizer,
                                     activity_regularizer=activity_regularizer,
                                     kernel_constraint=kernel_constraint,
                                     bias_constraint=bias_constraint
                                     )(upper_layer)
        return layer_output

    def Reshape(self, layer_information, input_name, layer_name):
        upper_name = self.models[input_name[0]]

        print(layer_information)
        name = layer_name
        target_shape = parse_number(layer_information,layer_name, 'target_shape')

        layer_output = layers.Reshape(target_shape=target_shape,
                                      name=name)(upper_name)

        return layer_output


    def Flatten(self, layer_information, input_name, layer_name):
        upper_layer = self.models[input_name[0]]
        name = layer_name
        data_format = parse_None(layer_information, 'data_format')

        layer_output = layers.Flatten(name=name,
                                       data_format=data_format
                                       )(upper_layer)
        return layer_output

    def MaxPool2D(self, layer_information, input_name, layer_name):
        upper_layer = self.models[input_name[0]]

        pool_size = parse_number(layer_information, layer_name, 'pool_size')
        strides = parse_number(layer_information, layer_name, 'strides')
        padding = parse_None(layer_information, 'padding')
        data_format = parse_None(layer_information, 'data_format')
        name = layer_name

        layer_output = layers.MaxPooling2D(pool_size=pool_size,
                                            strides=strides,
                                            padding=padding,
                                            data_format=data_format,
                                            name=name
                                            )(upper_layer)
        return layer_output



    def BatchNormalization(self, layer_information, input_name, layer_name):
        upper_layer = self.models[input_name[0]]

        axis = parse_number(layer_information, layer_name, 'axis')
        momentum = parse_number(layer_information, layer_name, 'momentum')
        epsilon = parse_number(layer_information, layer_name, 'epsilon')
        center = parse_None(layer_information, 'center')
        scale = parse_None(layer_information, 'scale')
        beta_initializer = parse_None(layer_information, 'beta_initializer')
        gamma_initializer = parse_None(layer_information, 'gamma_initializer')
        moving_mean_initializer = parse_None(layer_information, 'moving_mean_initializer')
        moving_variance_initializer = parse_None(layer_information, 'moving_variance_initializer')
        beta_regularizer = parse_None(layer_information, 'beta_regularizer')
        gamma_regularizer = parse_None(layer_information, 'gamma_regularizer')
        beta_constraint = parse_None(layer_information, 'beta_constraint')
        gamma_constraint = parse_None(layer_information, 'gamma_constraint')
        name = layer_name

        layer_output = layers.BatchNormalization()(upper_layer)

        # layer_output = layers.BatchNormalization(axis=axis,
        #                                          momentum=momentum,
        #                                          epsilon=epsilon,
        #                                          center=center,
        #                                          scale=scale,
        #                                          beta_initializer=beta_initializer,
        #                                          gamma_initializer=gamma_initializer,
        #                                          moving_mean_initializer=moving_mean_initializer,
        #                                          moving_variance_initializer=moving_variance_initializer,
        #                                          beta_regularizer=beta_regularizer,
        #                                          gamma_regularizer=gamma_regularizer,
        #                                          beta_constraint=beta_constraint,
        #                                          gamma_constraint=gamma_constraint,
        #                                          name=name
        #                                        )(upper_layer)
        return layer_output


    def Dropout(self, layer_information, input_name, layer_name):
        upper_layer = self.models[input_name[0]]

        try:
            rate = parse_number(layer_information, layer_name, 'rate')
            noise_shape = parse_None(layer_information, 'noise_shape')
            seed = parse_None(layer_information, 'seed')

            layer_output = layers.Dropout(rate=rate,
                                          noise_shape=noise_shape,
                                          seed=seed,
                                          name=layer_name)(upper_layer)

            return layer_output
        except Exception as e:
            print("---------------")
            print(e)

    def Permute(self, layer_information, input_name, layer_name):
        upper_layer = self.models[input_name[0]]

        dims = parse_number(layer_information, layer_name, 'dims')
        name = layer_name

        layer_output = layers.Permute(dims=dims,
                                      name=name)(upper_layer)
        return layer_output

    def Conv1D(self, layer_information, input_name, layer_name):
        upper_layer = self.models[input_name[0]]

        filters = parse_number(layer_information, layer_name, 'filters')
        kernel_size = parse_number(layer_information, layer_name, 'kernel_size')
        strides = parse_number(layer_information, layer_name, 'strides')
        padding = parse_None(layer_information, 'padding')
        data_format = parse_None(layer_information, 'data_format')
        dilation_rate = parse_number(layer_information, layer_name, 'dilation_rate')
        groups = parse_number(layer_information, layer_name, 'groups')
        activation = parse_None(layer_information, 'activation')
        use_bias = parse_None(layer_information, 'use_bias')
        kernel_initializer = parse_None(layer_information, 'kernel_initializer')
        bias_initializer = parse_None(layer_information, 'bias_initializer')
        kernel_regularizer = parse_None(layer_information, 'kernel_regularizer')
        bias_regularizer = parse_None(layer_information, 'bias_regularizer')
        activity_regularizer = parse_None(layer_information, 'bias_regularizer')
        kernel_constraint = parse_None(layer_information, 'kernel_constraint')
        bias_constraint = parse_None(layer_information, 'bias_constraint')
        name = layer_name

        layer_output = layers.Conv1D(filters=filters,
                                    kernel_size=kernel_size,
                                    strides=strides,
                                    padding=padding,
                                    data_format=data_format,
                                    dilation_rate=dilation_rate,
                                    groups=groups,
                                    activation=activation,
                                    use_bias=use_bias,
                                    kernel_initializer=kernel_initializer,
                                    bias_initializer=bias_initializer,
                                    kernel_regularizer=kernel_regularizer,
                                    bias_regularizer=bias_regularizer,
                                    activity_regularizer=activity_regularizer,
                                    kernel_constraint=kernel_constraint,
                                    bias_constraint=bias_constraint,
                                    name=name
                                    )(upper_layer)
        return layer_output

    def UpSampling1D(self, layer_information, input_name, layer_name):
        upper_layer = self.models[input_name[0]]

        size = parse_number(layer_information, layer_name, 'size')
        name = layer_name

        layer_output = layers.UpSampling1D(size=size,
                                          name=name
                                          )(upper_layer)
        return layer_output

    def UpSampling2D(self, layer_information, input_name, layer_name):
        upper_layer = self.models[input_name[0]]

        size = parse_number(layer_information, layer_name, 'size')
        data_format = parse_None(layer_information, 'data_format')
        interpolation = parse_None(layer_information, 'interpolation')
        name = layer_name

        layer_output = layers.UpSampling2D(size=size,
                                          data_format=data_format,
                                          interpolation=interpolation,
                                          name=name
                                          )(upper_layer)
        return layer_output

    def ZeroPadding1D(self, layer_information, input_name, layer_name):
        upper_layer = self.models[input_name[0]]

        padding = parse_number(layer_information, layer_name, 'padding')
        name = layer_name

        layer_output = layers.ZeroPadding1D(padding=padding,
                                           name=name
                                           )(upper_layer)
        return layer_output

    def ZeroPadding2D(self, layer_information, input_name, layer_name):
        upper_layer = self.models[input_name[0]]

        padding = parse_number(layer_information, layer_name, 'padding')
        data_format = parse_None(layer_information, 'data_format')
        name = layer_name

        layer_output = layers.ZeroPadding2D(padding=padding,
                                           data_format=data_format,
                                           name=name
                                           )(upper_layer)
        return layer_output

    def MaxPooling1D(self, layer_information, input_name, layer_name):
        upper_layer = self.models[input_name[0]]

        pool_size = parse_number(layer_information, layer_name, 'pool_size')
        strides = parse_number(layer_information, layer_name, 'strides')
        padding = parse_None(layer_information, 'padding')
        data_format = parse_None(layer_information, 'data_format')
        name = layer_name

        layer_output = layers.MaxPooling1D(pool_size=pool_size,
                                          strides=strides,
                                          padding=padding,
                                          data_format=data_format,
                                          name=name
                                          )(upper_layer)
        return layer_output

    def AveragePooling1D(self, layer_information, input_name, layer_name):
        upper_layer = self.models[input_name[0]]

        pool_size = parse_number(layer_information, layer_name, 'pool_size')
        strides = parse_number(layer_information, layer_name, 'strides')
        padding = parse_None(layer_information, 'padding')
        data_format = parse_None(layer_information, 'data_format')
        name = layer_name

        layer_output = layers.AveragePooling1D(pool_size=pool_size,
                                              strides=strides,
                                              padding=padding,
                                              data_format=data_format,
                                              name=name
                                              )(upper_layer)
        return layer_output

    def AveragePooling2D(self, layer_information, input_name, layer_name):
        upper_layer = self.models[input_name[0]]

        pool_size = parse_number(layer_information, layer_name, 'pool_size')
        strides = parse_number(layer_information, layer_name, 'strides')
        padding = parse_None(layer_information, 'padding')
        data_format = parse_None(layer_information, 'data_format')
        name = layer_name

        layer_output = layers.AveragePooling2D(pool_size=pool_size,
                                              strides=strides,
                                              padding=padding,
                                              data_format=data_format,
                                              name=name
                                              )(upper_layer)
        return layer_output

    def GlobalMaxPooling1D(self, layer_information, input_name, layer_name):
        upper_layer = self.models[input_name[0]]

        data_format = parse_None(layer_information, 'data_format')
        keepdims = parse_None(layer_information, 'keepdims')
        name = layer_name

        layer_output = layers.GlobalMaxPooling1D(data_format=data_format,
                                                keepdims=keepdims,
                                                name=name
                                                )(upper_layer)
        return layer_output

    def GlobalMaxPooling2D(self, layer_information, input_name, layer_name):
        upper_layer = self.models[input_name[0]]

        data_format = parse_None(layer_information, 'data_format')
        keepdims = parse_None(layer_information, 'keepdims')
        name = layer_name

        layer_output = layers.GlobalMaxPooling2D(data_format=data_format,
                                                keepdims=keepdims,
                                                name=name
                                                )(upper_layer)
        return layer_output

    def GlobalAveragePooling1D(self, layer_information, input_name, layer_name):
        upper_layer = self.models[input_name[0]]

        data_format = parse_None(layer_information, 'data_format')
        keepdims = parse_None(layer_information, 'keepdims')
        name = layer_name

        layer_output = layers.GlobalAveragePooling1D(data_format=data_format,
                                                    keepdims=keepdims,
                                                    name=name
                                                    )(upper_layer)
        return layer_output

    def GlobalAveragePooling2D(self, layer_information, input_name, layer_name):
        upper_layer = self.models[input_name[0]]

        data_format = parse_None(layer_information, 'data_format')
        keepdims = parse_None(layer_information, 'keepdims')

        layer_output = layers.GlobalAveragePooling2D(data_format=data_format,
                                                      keepdims=keepdims,
                                                      name=layer_name)(upper_layer)

        return layer_output


    def Embedding(self, layer_information, input_name, layer_name):
        upper_layer = self.models[input_name[0]]

        input_dim = parse_number(layer_information, layer_name, 'input_dim')
        output_dim = parse_number(layer_information, layer_name, 'output_dim')
        embeddings_initializer = parse_None(layer_information, 'embeddings_initializer')
        embeddings_regularizer = parse_None(layer_information, 'embeddings_regularizer')
        activity_regularizer = parse_None(layer_information, 'activity_regularizer')
        embeddings_constraint = parse_None(layer_information, 'embeddings_constraint')
        mask_zero = parse_None(layer_information, 'mask_zero')
        input_length = parse_number(layer_information, layer_name, 'input_length')  # 这个不知道理解的对不对
        name = layer_name

        layer_out = layers.Embedding(input_dim=input_dim,
                                    output_dim=output_dim,
                                    embeddings_initializer=embeddings_initializer,
                                    embeddings_regularizer=embeddings_regularizer,
                                    activity_regularizer=activity_regularizer,
                                    embeddings_constraint=embeddings_constraint,
                                    mask_zero=mask_zero,
                                    input_length=input_length,
                                    name=name
                                    )(upper_layer)
        return layer_out

    def Relu(self, layer_information, input_name, layer_name):
        upper_layer = self.models[input_name[0]]

        max_value = parse_number(layer_information, layer_name, 'max_value')
        negative_slope = parse_number(layer_information, layer_name, 'negative_slope') # 让c++ 那边做限制，如果没有输入参数，传过来0
        threshold = parse_number(layer_information, layer_name, 'threshold')
        name = layer_name

        layer_out = layers.ReLU(max_value=max_value,
                               negative_slope=negative_slope,
                               threshold=threshold,
                               name=name
                               )(upper_layer)
        return layer_out

    def Softmax(self, layer_information, input_name, layer_name):
        upper_layer = self.models[input_name[0]]

        name = layer_name

        layer_out = layers.Softmax(name=name)(upper_layer)
        return layer_out


    def ELU(self, layer_information, input_name, layer_name):
        upper_layer = self.models[input_name[0]]

        alpha = parse_number(layer_information, layer_name, 'alpha')

        name = layer_name

        layer_out = layers.ELU(alpha=alpha,
                               name=name
                               )(upper_layer)
        return layer_out

    def LeakyReLU(self, layer_information, input_name, layer_name):
        upper_layer = self.models[input_name[0]]

        alpha = parse_number(layer_information, layer_name, 'alpha')
        name = layer_name

        layer_out = layers.LeakyReLU(alpha=alpha,
                                     name=name
                                     )(upper_layer)
        return layer_out

    def PReLU(self, layer_information, input_name, layer_name):
        upper_layer = self.models[input_name[0]]

        alpha_initializer = parse_None(layer_information, 'alpha_initializer')
        alpha_regularizer = parse_None(layer_information, 'alpha_regularizer')
        alpha_constraint = parse_None(layer_information, 'alpha_constraint')
        shared_axes = parse_None(layer_information, 'shared_axes')
        name = layer_name

        layer_out = layers.PReLU(alpha_initializer=alpha_initializer,
                                            alpha_regularizer=alpha_regularizer,
                                            alpha_constraint=alpha_constraint,
                                            shared_axes=shared_axes,
                                            name=name
                                            )(upper_layer)
        return layer_out


    # ------------------------------------------------------------------------------

    def Concatenate(self, layer_information, input_name, layer_name):
        upper_layer = []
        for name in input_name:
            upper_layer.append(self.models[name])

        axis = parse_number(layer_information, layer_name, 'axis')
        try:
            layer_output = layers.Concatenate(axis=axis,
                                              name=layer_name)(upper_layer)
            return layer_output
        except Exception as e:
            event_logger.error(f"An error occurred at the {layer_name}, the specific error message is:")
            event_logger.error(e)

    def Add(self, layer_information, input_name, layer_name):
        upper_layer = []
        for name in input_name:
            upper_layer.append(self.models[name])

        try:
            layer_output = layers.add(upper_layer)
            return layer_output
        except Exception as e:
            event_logger.error(f"An error occurred at the {layer_name}, the specific error message is:")
            event_logger.error(e)



class BuildModelOptimizer(object):

    def __init__(self):
        pass

    def build(self, optimizer_info):
        return getattr(self, optimizer_info['optimizer'])(optimizer_info)


    def Adadelta(self, optimizer_info):
        learning_rate = float(optimizer_info['learning_rate'] if optimizer_info['learning_rate'] != '' else 0.001)
        rho = float(optimizer_info['rho'] if optimizer_info['rho'] != '' else 0.95)
        epsilon = float(optimizer_info['epsilon'] if optimizer_info['epsilon'] != '' else 1e-07)

        return optimizers.Adadelta(learning_rate=learning_rate,
                                        rho=rho,
                                        epsilon=epsilon)

    def Adagrad(self, optimizer_info):
        learning_rate = float(optimizer_info['learning_rate'] if optimizer_info['learning_rate'] != '' else 0.001)
        initial_accumulator_value = float(optimizer_info['initial_accumulator_value']
                                          if optimizer_info['initial_accumulator_value'] != '' else 0.1)
        epsilon = float(optimizer_info['epsilon'] if optimizer_info['epsilon'] != '' else 1e-07)

        return optimizers.Adagrad(learning_rate=learning_rate,
                                       initial_accumulator_value=initial_accumulator_value,
                                       epsilon=epsilon)

    def Adam(self, optimizer_info):
        learning_rate = float(optimizer_info['learning_rate'] if optimizer_info['learning_rate'] != '' else 0.001)
        # beta_1 = float(optimizer_info['beta_1'] if optimizer_info['beta_1'] != '' else 0.9)
        # beta_2 = float(optimizer_info['beta_2'] if optimizer_info['beta_2'] != '' else 0.999)
        # epsilon = float(optimizer_info['epsilon'] if optimizer_info['epsilon'] != '' else 1e-07)
        # amsgrad = parse_None(optimizer_info, 'amsgrad')  # 这里传入的值是 True/False 所以可以使用 parse_None 解析

        return optimizers.Adam(learning_rate=learning_rate)

    def Adamax(self, optimizer_info):
        learning_rate = float(optimizer_info['learning_rate'] if optimizer_info['learning_rate'] != '' else 0.001)
        beta_1 = float(optimizer_info['beta_1'] if optimizer_info['beta_1'] != '' else 0.9)
        beta_2 = float(optimizer_info['beta_2'] if optimizer_info['beta_2'] != '' else 0.999)
        epsilon = float(optimizer_info['epsilon'] if optimizer_info['epsilon'] != '' else 1e-07)

        return optimizers.Adamax(learning_rate=learning_rate,
                                      beta_1=beta_1,
                                      beta_2=beta_2,
                                      epsilon=epsilon)

    def Ftrl(self, optimizer_info):
        learning_rate = float(optimizer_info['learning_rate'] if optimizer_info['learning_rate'] != '' else 0.001)
        learning_rate_power = float(optimizer_info['learning_rate_power']
                                    if optimizer_info['learning_rate_power'] != '' else -0.5)
        initial_accumulator_value = float(optimizer_info['initial_accumulator_value']
                                          if optimizer_info['initial_accumulator_value'] != '' else 0.1)
        l1_regularization_strength = float(optimizer_info['l1_regularization_strength']
                                           if optimizer_info['l1_regularization_strength'] != '' else 0.0)
        l2_regularization_strength = float(optimizer_info['l2_regularization_strength']
                                           if optimizer_info['l2_regularization_strength'] != '' else 0.0)
        l2_shrinkage_regularization_strength = float(optimizer_info['l2_shrinkage_regularization_strength']
                                                     if optimizer_info[
                                                            'l2_shrinkage_regularization_strength'] != '' else 0.0)
        beta = float(optimizer_info['beta']
                     if optimizer_info['beta'] != '' else 0.0)
        return optimizers.Ftrl(learning_rate=learning_rate,
                                    learning_rate_power=learning_rate_power,
                                    initial_accumulator_value=initial_accumulator_value,
                                    l1_regularization_strength=l1_regularization_strength,
                                    l2_regularization_strength=l2_regularization_strength,
                                    l2_shrinkage_regularization_strength=l2_shrinkage_regularization_strength,
                                    beta=beta)

    def Nadam(self, optimizer_info):
        learning_rate = float(optimizer_info['learning_rate'] if optimizer_info['learning_rate'] != '' else 0.001)
        beta_1 = float(optimizer_info['beta_1'] if optimizer_info['beta_1'] != '' else 0.9)
        beta_2 = float(optimizer_info['beta_2'] if optimizer_info['beta_2'] != '' else 0.999)
        epsilon = float(optimizer_info['epsilon'] if optimizer_info['epsilon'] != '' else 1e-07)

        return optimizers.Nadam(learning_rate=learning_rate,
                                     beta_1=beta_1,
                                     beta_2=beta_2,
                                     epsilon=epsilon)

    def Optimizer(self, optimizer_info):
        gradient_aggregator = parse_None(optimizer_info, 'gradient_aggregator')
        gradient_transformers = parse_None(optimizer_info, 'gradient_transformers')

        return optimizers.Optimizer(gradient_aggregator=gradient_aggregator,
                                         gradient_transformers=gradient_transformers)

    def RMSprop(self, optimizer_info):
        layer_name = optimizer_info['optimizer']

        learning_rate = float(optimizer_info['learning_rate'] if optimizer_info['learning_rate'] != '' else 0.001)
        rho = float(optimizer_info['rho'] if optimizer_info['rho'] != '' else 0.9)
        momentum = float(optimizer_info['momentum'] if optimizer_info['momentum'] != '' else 0.0)
        epsilon = float(optimizer_info['epsilon'] if optimizer_info['epsilon'] != '' else 1e-07)
        centered = parse_None(optimizer_info, 'centered')

        return optimizers.RMSprop(learning_rate=learning_rate,
                                       rho=rho,
                                       momentum=momentum,
                                       epsilon=epsilon,
                                       centered=centered)

    def SGD(self, optimizer_info):
        layer_name = optimizer_info['optimizer']

        learning_rate = float(optimizer_info['learning_rate'] if optimizer_info['learning_rate'] != '' else 0.01)
        momentum = float(optimizer_info['momentum'] if optimizer_info['momentum'] != '' else 0.0)
        nesterov = parse_None(optimizer_info, 'nesterov')

        return optimizers.SGD(learning_rate=learning_rate,
                                   momentum=momentum,
                                   nesterov=nesterov)

class BuildModelLosses(object):

    def __init__(self):
        pass

    def build(self, losses_info):
        return getattr(self, losses_info['loss_name'])(losses_info)

    def BinaryCrossentropy(self, losses_info):


        from_logits = parse_None(losses_info, 'from_logits')
        label_smoothing = float(losses_info['label_smoothing'] if losses_info['label_smoothing'] != '' else 0.95)
        axis = parse_number(losses_info, 'axis')
        reduction = parse_None(losses_info, 'reduction')

        return losses.BinaryCrossentropy(from_logits=from_logits,
                                              label_smoothing=label_smoothing,
                                              axis=axis,
                                              reduction=reduction)

    def CategoricalCrossentropy(self, losses_info):
        from_logits = parse_None(losses_info, 'from_logits')
        label_smoothing = float(losses_info['label_smoothing'] if losses_info['label_smoothing'] != '' else 0.95)
        axis = parse_number(losses_info, 'axis')
        reduction = parse_None(losses_info, 'reduction')    # 'auto', 'none', 'sum', 'sum_over_batch_size'

        return losses.CategoricalCrossentropy(from_logits=from_logits,
                                                   label_smoothing=label_smoothing,
                                                   axis=axis,
                                                   reduction=reduction)

    def CategoricalHinge(self, losses_info):
        reduction = parse_None(losses_info, 'reduction')

        return losses.CategoricalHinge(reduction=reduction)

    def CosineSimilarity(self, losses_info):
        axis = parse_number(losses_info, 'axis')
        reduction = parse_None(losses_info, 'reduction')

        return losses.CosineSimilarity(axis=axis,
                                            reduction=reduction)

    def Hinge(self, losses_info):
        reduction = parse_None(losses_info, 'reduction')

        return losses.Hinge(reduction=reduction)

    def Huber(self, losses_info):
        delta = float(losses_info['delta'] if losses_info['delta'] != '' else 1.0)
        reduction = parse_None(losses_info, 'reduction')

        return losses.Huber(delta=delta,
                                 reduction=reduction)

    def KLDivergence(self, losses_info):
        reduction = parse_None(losses_info, 'reduction')

        return losses.KLDivergence(reduction=reduction)

    def LogCosh(self, losses_info):
        reduction = parse_None(losses_info, 'reduction')

        return losses.LogCosh(reduction=reduction)

    def Loss(self, losses_info):
        reduction = parse_None(losses_info, 'reduction')

        return losses.Loss(reduction=reduction)

    def MeanAbsoluteError(self, losses_info):
        reduction = parse_None(losses_info, 'reduction')

        return losses.MeanAbsoluteError(reduction=reduction)

    def MeanAbsolutePercentageError(self, losses_info):
        reduction = parse_None(losses_info, 'reduction')

        return losses.MeanAbsolutePercentageError(reduction=reduction)

    def MeanSquaredError(self, losses_info):
        reduction = parse_None(losses_info, 'reduction')

        return losses.MeanSquaredError(reduction=reduction)

    def MeanSquaredLogarithmicError(self, losses_info):
        reduction = parse_None(losses_info, 'reduction')

        return losses.MeanSquaredLogarithmicError(reduction=reduction)

    def Poisson(self, losses_info):
        reduction = parse_None(losses_info, 'reduction')

        return losses.Poisson(reduction=reduction)

    def SparseCategoricalCrossentropy(self, losses_info):
        from_logits = parse_None(losses_info, 'from_logits')
        reduction = parse_None(losses_info, 'reduction')

        return losses.SparseCategoricalCrossentropy(from_logits=from_logits,
                                                         reduction=reduction)

    def SquaredHinge(self, losses_info):
        reduction = parse_None(losses_info, 'reduction')

        return losses.SquaredHinge(reduction=reduction)

class BuildModelFit(object):

    def fit(self, model, training_data, fit_info):
        layer_name = 'Fit'

        batch_size = parse_number(fit_info, layer_name, 'batch_size')
        epochs = parse_number(fit_info, layer_name, 'epochs')
        # verbose = parse_number(fit_info, 'verbose')   # auto, 1, 2, 3
        # # validation_split = parse_number(fit_info, 'validation_split')
        # validation_split = None
        # validation_data = parse_None(fit_info, 'validation_data')
        # shuffle = parse_None(fit_info, 'shuffle')
        # # shuffle = None
        # class_weight = parse_None(fit_info, 'class_weight')
        # sample_weight = parse_None(fit_info, 'sample_weight')
        # # initial_epoch = parse_number(fit_info, 'initial_epoch')
        # initial_epoch = 0
        # steps_per_epoch = parse_number(fit_info, 'steps_per_epoch')
        # validation_steps = parse_number(fit_info, 'validation_steps')
        # validation_batch_size = parse_number(fit_info, 'validation_batch_size')
        # validation_freq = parse_number(fit_info, 'validation_freq')
        # max_queue_size = parse_number(fit_info, 'max_queue_size')
        # workers = parse_number(fit_info, 'workers')
        # use_multiprocessing = parse_None(fit_info, 'use_multiprocessing')
        # break_point = parse_number(fit_info, 'break_point')
        break_point = 50

        test_data = training_data['imageInputLayer0']['test_data']
        test_label = training_data['imageInputLayer0']['test_label']

        class CustomCallback(callbacks.Callback):

            def __init__(self):
                super().__init__()
                self.batch_step = 0
                self.epoch_step = 0


            def on_train_batch_end(self, batch, logs=None):
                self.batch_step += 1
                logs['batch_size'] = self.batch_step
                try:
                    if self.batch_step % break_point == 0 or self.batch_step == 1 :
                        print("test_start")
                        test_loss, test_acc = self.model.evaluate(test_data, test_label, verbose=2)

                        test_result = {'loss': test_loss, 'accuracy': test_acc, 'batch_size': self.batch_step}
                        emit(
                            identifier="network.test",
                            data=test_result
                        )
                except Exception as e:
                    print(e)
                emit(
                    identifier='network.train',
                    data=logs
                )

            def on_epoch_end(self, epoch, logs=None):
                self.epoch_step += 1
                if self.epoch_step == epochs:
                    test_loss, test_acc = self.model.evaluate(test_data, test_label, verbose=2)

                    test_result = {'loss': test_loss, 'accuracy': test_acc, 'batch_size': self.batch_step}
                    emit(
                        identifier="network.finish",
                        data=test_result
                    )


        callback_list = []
        custom_callback = CustomCallback()
        callback_list.append(custom_callback)
        # callback_dic = parse_None(fit_info, 'callbacks')
        # print(type(training_data['imageInputLayer0']['train_data']))
        # print(training_data['imageInputLayer0']['train_data'].shape)
        # print(training_data['imageInputLayer0']['train_label'].shape)
        # print(training_data['imageInputLayer0']['test_data'].shape)
        # print(training_data['imageInputLayer0']['test_label'].shape)
        # x = []
        # y = []
        # for name in model.input_names:
        #     x.append(training_data[name]['train_data'])
        #     y.append(training_data[name]['train_label'])
        # batch_size = 32
        # epochs = 10

        try:
            history = model.fit(
                training_data['imageInputLayer0']['train_data'],
                training_data['imageInputLayer0']['train_label'],
                batch_size = batch_size,
                epochs = epochs,
                callbacks=callback_list
                # verbose = verbose,
                # validation_split=validation_split,
                # validation_data=validation_data,
                # shuffle = shuffle,
                # class_weight=class_weight,
                # sample_weight=sample_weight,
                # initial_epoch=initial_epoch,
                # steps_per_epoch=steps_per_epoch,
                # validation_steps=validation_steps,
                # validation_batch_size=validation_batch_size,
                # validation_freq=validation_freq,
                # max_queue_size=max_queue_size,
                # workers=workers,
                # use_multiprocessing=use_multiprocessing
            )
        except Exception as e:
            # import traceback
            # traceback.print_exc()
            event_logger.error(e)

class BuildModel(object):

    def __init__(self):
        self.model = None
        self.training_data = {}
        self.compile_flag = True

    def nn_build(self, kwargs: dict) -> None:
        logger.info("Start nn_build:")
        try:
            data = clean_parameter(kwargs)  # layers 将每层的层名和其属性对应起来  # order 表示神经网络中隐含层的先后建立顺序
        except:
            logger.error("model structure error !!")
            event_logger.error("in model building, model_structure is error! Please check.")
            raise Exception("break code")

        filePath = data['train_data_path']
        test_size = data['verify_data_rate'] / 100
        path = pathlib.Path(filePath)
        if path.exists():
            if path.is_file():
                event_logger.error('your input is file, not directory')
                raise Exception("break code")
        else:
            event_logger.error("the entered directory does not exist")
            raise Exception("break code")

        event_logger.info("Start loading data !!!")
        for input_name in data['input_layers']:
            input_shape = data['layers'][input_name]['config']['shape']
            res= load_data(filePath, input_shape, test_size)
            self.training_data[input_name] = res

        event_logger.info("Data loaded successfully !!!")

        try:
            event_logger.info("Start building the model !!!")
            # if data['build_type'] == 2:
            #     try:
            #         self.model = keras.models.load_model(data["h5_file_name"])
            #     except:

            #         event_logger.error("Incorrect file path ！！！")
            # else:
            model = BuildModelLayers()
            self.model = model.build(data)
            res = {"input_name": self.model.input_names, "output_name": self.model.output_names}
            emit(identifier="NeuralNetworks.model_information", data=res)
            event_logger.info("Model built successfully")
        except:
            self.model = None
            event_logger.error("In model building, model_structure is error! Please check.")
            raise Exception("break code")

    def nn_compile(self, kwargs: dict) -> None:
        event_logger.info('Start compiling the model !!!')
        self.compile_flag = True
        if not self.model:
            event_logger.error("you haven't build the model, please build the model first!")
            raise Exception('break code')
        try:
            compile_info = kwargs['compile']

            optimizer = BuildModelOptimizer()
            optimizer = optimizer.build(compile_info)

            # loss = BuildModelLosses()
            # losses_info = kwargs['losses_info']
            # loss = loss.build(losses_info)

            loss = parse_None(compile_info, 'loss')
            metrics = parse_None(compile_info, 'metrics')
            # optimizer = parse_None(compile_info, 'optimizer')
            # loss = parse_None(compile_info, 'loss')
            # optimizer = 'adam'
            # loss = 'sparse_categorical_crossentropy'
            # metrics = compile_info['metrics']
            # metrics = 'accuracy'
            run_eagerly = None
            steps_per_execution =None
            weighted_metrics = None
            # weighted_metrics = None
            self.model.compile(
                optimizer=optimizer,
                loss=loss,
                metrics=metrics,
                run_eagerly=run_eagerly,
                steps_per_execution=steps_per_execution,
                weighted_metrics=weighted_metrics
            )
            event_logger.info("Model compiled successfully !!!")
        except Exception as e:
            self.compile_flag = False
            event_logger.error(e)
            event_logger.error("In model compiling, information is error! Please check.")
            raise Exception("break code")
        # self.nn_train(kwargs)

    def nn_train(self, kwargs=None):
        event_logger.info("Start training the model !!!")

        if not self.model:
            event_logger.error("Please build the model first!")
            raise Exception("break code")
        if not self.compile_flag:
            event_logger.error("Please compile the model first!")
            raise Exception("break code")
        fit_info = kwargs['fit']
        try:
            buildModelFit = BuildModelFit()
            history = buildModelFit.fit(self.model, self.training_data, fit_info)
            event_logger.info("Model trained successfully !!!")
        except :
            event_logger.error("Error during model training !!")
            event_logger.error(traceback.print_exc())


model = BuildModel()
def net_interface(kwargs):
    kwargs = json.loads(kwargs)
    if kwargs.get("function") == "build":
        model.nn_build(kwargs)
    elif kwargs.get("function") == "compile_fit":
        model.nn_compile(kwargs)
        model.nn_train(kwargs)


