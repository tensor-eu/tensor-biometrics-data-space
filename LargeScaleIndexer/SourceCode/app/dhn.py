import os
from datetime import datetime
from math import ceil
import numpy as np
import tensorflow as tf


np.random.seed(1)
# random.seed(1)
tf.random.set_seed(1)
tf.config.experimental.enable_op_determinism()
os.environ['TF_DETERMINISTIC_OPS'] = '1'


def img_alexnet_layers(img, batch_size, output_dim, stage, weights_file, with_tanh=True, val_batch_size=32, headers=""):
    tf.random.set_seed(1)
    deep_param_img = {}
    train_layers = []
    train_last_layer = []

    # net_data = dict(np.load(model_weights, encoding='bytes').item()) 
    # net_data = dict(np.load(model_weights, allow_pickle=True, encoding='bytes').item())
    # weights_file.seek(0)
    # net_data = dict(np.load(weights_file, allow_pickle=True, encoding='bytes').item())

    net_data = dict(weights_file)


    # print(list(net_data.keys()))

    # swap(2,1,0), bgr -> rgb
    reshaped_image = tf.cast(img, tf.float32)[:, :, :, ::-1]

    height = 227
    width = 227

    # Randomly crop a [height, width] section of each image
    with tf.name_scope('preprocess'):
        def train_fn():
            tf.random.set_seed(1)
            return tf.stack([tf.image.random_crop(tf.image.random_flip_left_right(each), [height, width, 3])
                             for each in tf.unstack(reshaped_image, batch_size)])

        def val_fn():
            tf.random.set_seed(1)
            unstacked = tf.unstack(reshaped_image, val_batch_size)

            def crop(img, x, y): return tf.image.crop_to_bounding_box(
                img, x, y, width, height)

            def distort(f, x, y): return tf.stack(
                [crop(f(each), x, y) for each in unstacked])

            def distort_raw(x, y): return distort(lambda x: x, x, y)

            def distort_fliped(x, y): return distort(
                tf.image.flip_left_right, x, y)
            distorted = tf.concat([distort_fliped(0, 0), distort_fliped(28, 0),
                                   distort_fliped(
                                       0, 28), distort_fliped(28, 28),
                                   distort_fliped(14, 14), distort_raw(0, 0),
                                   distort_raw(28, 0), distort_raw(0, 28),
                                   distort_raw(28, 28), distort_raw(14, 14)], 0)

            return distorted
        distorted = tf.cond(stage > 0, val_fn, train_fn)

        # Zero-mean input
        mean = tf.constant([123.68, 116.779, 103.939], dtype=tf.float32, shape=[
                           1, 1, 1, 3], name='img-mean')
        distorted = distorted - mean

    # Conv1
    # Output 96, kernel 11, stride 4
    with tf.name_scope('conv1') as scope:
        kernel = tf.Variable(net_data['conv1'][0], name='weights')
        conv = tf.nn.conv2d(distorted, kernel, [1, 4, 4, 1], padding='VALID')
        biases = tf.Variable(net_data['conv1'][1], name='biases')
        out = tf.nn.bias_add(conv, biases)
        conv1 = tf.nn.relu(out, name=scope)
        deep_param_img['conv1'] = [kernel, biases]
        train_layers += [kernel, biases]

    # Pool1
    pool1 = tf.nn.max_pool2d(conv1,
                           ksize=[1, 3, 3, 1],
                           strides=[1, 2, 2, 1],
                           padding='VALID',
                           name='pool1')

    # LRN1
    radius = 2
    alpha = 2e-05
    beta = 0.75
    bias = 1.0
    lrn1 = tf.nn.local_response_normalization(pool1,
                                              depth_radius=radius,
                                              alpha=alpha,
                                              beta=beta,
                                              bias=bias)

    # Conv2
    # Output 256, pad 2, kernel 5, group 2
    with tf.name_scope('conv2') as scope:
        kernel = tf.Variable(net_data['conv2'][0], name='weights')
        group = 2

        def convolve(i, k): return tf.nn.conv2d(
            i, k, [1, 1, 1, 1], padding='SAME')
        input_groups = tf.split(lrn1, group, 3)
        kernel_groups = tf.split(kernel, group, 3)
        output_groups = [convolve(i, k)
                         for i, k in zip(input_groups, kernel_groups)]
        # Concatenate the groups
        conv = tf.concat(output_groups, 3)

        biases = tf.Variable(net_data['conv2'][1], name='biases')
        out = tf.nn.bias_add(conv, biases)
        conv2 = tf.nn.relu(out, name=scope)
        deep_param_img['conv2'] = [kernel, biases]
        train_layers += [kernel, biases]

    # Pool2
    pool2 = tf.nn.max_pool2d(conv2,
                           ksize=[1, 3, 3, 1],
                           strides=[1, 2, 2, 1],
                           padding='VALID',
                           name='pool2')

    # LRN2
    radius = 2
    alpha = 2e-05
    beta = 0.75
    bias = 1.0
    lrn2 = tf.nn.local_response_normalization(pool2,
                                              depth_radius=radius,
                                              alpha=alpha,
                                              beta=beta,
                                              bias=bias)

    # Conv3
    # Output 384, pad 1, kernel 3
    with tf.name_scope('conv3') as scope:
        kernel = tf.Variable(net_data['conv3'][0], name='weights')
        conv = tf.nn.conv2d(lrn2, kernel, [1, 1, 1, 1], padding='SAME')
        biases = tf.Variable(net_data['conv3'][1], name='biases')
        out = tf.nn.bias_add(conv, biases)
        conv3 = tf.nn.relu(out, name=scope)
        deep_param_img['conv3'] = [kernel, biases]
        train_layers += [kernel, biases]

    # Conv4
    # Output 384, pad 1, kernel 3, group 2
    with tf.name_scope('conv4') as scope:
        kernel = tf.Variable(net_data['conv4'][0], name='weights')
        group = 2

        def convolve(i, k): return tf.nn.conv2d(
            i, k, [1, 1, 1, 1], padding='SAME')
        input_groups = tf.split(conv3, group, 3)
        kernel_groups = tf.split(kernel, group, 3)
        output_groups = [convolve(i, k)
                         for i, k in zip(input_groups, kernel_groups)]
        # Concatenate the groups
        conv = tf.concat(output_groups, 3)
        biases = tf.Variable(net_data['conv4'][1], name='biases')
        out = tf.nn.bias_add(conv, biases)
        conv4 = tf.nn.relu(out, name=scope)
        deep_param_img['conv4'] = [kernel, biases]
        train_layers += [kernel, biases]

    # Conv5
    # Output 256, pad 1, kernel 3, group 2
    with tf.name_scope('conv5') as scope:
        kernel = tf.Variable(net_data['conv5'][0], name='weights')
        group = 2

        def convolve(i, k): return tf.nn.conv2d(
            i, k, [1, 1, 1, 1], padding='SAME')
        input_groups = tf.split(conv4, group, 3)
        kernel_groups = tf.split(kernel, group, 3)
        output_groups = [convolve(i, k)
                         for i, k in zip(input_groups, kernel_groups)]
        # Concatenate the groups
        conv = tf.concat(output_groups, 3)
        biases = tf.Variable(net_data['conv5'][1], name='biases')
        out = tf.nn.bias_add(conv, biases)
        conv5 = tf.nn.relu(out, name=scope)
        deep_param_img['conv5'] = [kernel, biases]
        train_layers += [kernel, biases]

    # Pool5
    pool5 = tf.nn.max_pool2d(conv5,
                           ksize=[1, 3, 3, 1],
                           strides=[1, 2, 2, 1],
                           padding='VALID',
                           name='pool5')

    # FC6
    # Output 4096
    with tf.name_scope('fc6'):
        shape = int(np.prod(pool5.get_shape()[1:]))
        fc6w = tf.Variable(net_data['fc6'][0], name='weights')
        fc6b = tf.Variable(net_data['fc6'][1], name='biases')
        pool5_flat = tf.reshape(pool5, [-1, shape])
        fc6l = tf.nn.bias_add(tf.matmul(pool5_flat, fc6w), fc6b)
        fc6 = tf.nn.relu(fc6l)
        fc6 = tf.cond(stage > 0, lambda: fc6, lambda: tf.nn.dropout(fc6, 0.5))
        fc6o = tf.nn.relu(fc6l)
        deep_param_img['fc6'] = [fc6w, fc6b]
        train_layers += [fc6w, fc6b]

    # FC7
    # Output 4096
    with tf.name_scope('fc7'):
        fc7w = tf.Variable(net_data['fc7'][0], name='weights')
        fc7b = tf.Variable(net_data['fc7'][1], name='biases')
        fc7l = tf.nn.bias_add(tf.matmul(fc6, fc7w), fc7b)
        fc7 = tf.nn.relu(fc7l)
        fc7 = tf.cond(stage > 0, lambda: fc7, lambda: tf.nn.dropout(fc7, 0.5))
        deep_param_img['fc7'] = [fc7w, fc7b]
        train_layers += [fc7w, fc7b]

    # FC8
    # Output output_dim
    with tf.name_scope('fc8'):
        # Differ train and val stage by 'fc8' as key
        if 'fc8' in net_data:
            fc8w = tf.Variable(net_data['fc8'][0], name='weights')
            fc8b = tf.Variable(net_data['fc8'][1], name='biases')
        else:
            fc8w = tf.Variable(tf.random.normal([4096, output_dim],
                                                dtype=tf.float32,
                                                stddev=1e-2), name='weights')
            fc8b = tf.Variable(tf.constant(0.0, shape=[output_dim],
                                           dtype=tf.float32), name='biases')
        fc8l = tf.nn.bias_add(tf.matmul(fc7, fc8w), fc8b)
        if with_tanh:
            fc8_t = tf.nn.tanh(fc8l)
        else:
            fc8_t = fc8l

        def val_fn1():
            concated = tf.concat([tf.expand_dims(i, 0)
                                  for i in tf.split(fc8_t, 10, 0)], 0)
            return tf.reduce_mean(concated, 0)
        fc8 = tf.cond(stage > 0, val_fn1, lambda: fc8_t)
        deep_param_img['fc8'] = [fc8w, fc8b]
        train_last_layer += [fc8w, fc8b]

    return fc8, deep_param_img, train_layers, train_last_layer


import numpy as np

class Dataset(object):
    def __init__(self, dataset, output_dim):
        print ("Initializing Dataset")
        self._dataset = dataset
        self.n_samples = dataset.n_samples
        self._train = dataset.train
        self._output = np.zeros((self.n_samples, output_dim), dtype=np.float32)
        self._paths =  np.empty((self.n_samples,), dtype=object)
        self._perm = np.arange(self.n_samples)
        np.random.shuffle(self._perm)
        self._index_in_epoch = 0
        self._epochs_complete = 0
        print ("Dataset already")
        return

    def next_batch(self, batch_size):
        """
        Args:
          batch_size
        Returns:
          [batch_size, (n_inputs)]: next batch images
          [batch_size, n_class]: next batch labels
          [batch_size, ]: next batch paths
        """
        start = self._index_in_epoch
        self._index_in_epoch += batch_size
        # Another epoch finish
        if self._index_in_epoch > self.n_samples:
            if self._train:
                # Training stage need repeating get batch
                self._epochs_complete += 1
                # Shuffle the data
                np.random.shuffle(self._perm)
                # Start next epoch
                start = 0
                self._index_in_epoch = batch_size
            else:
                # Validation stage only process once
                start = self.n_samples - batch_size
                self._index_in_epoch = self.n_samples
        end = self._index_in_epoch

        data, label, path = self._dataset.data(self._perm[start:end])
        
        return (data, label, path)

    def feed_batch_output(self, batch_size, output, paths):
        start = self._index_in_epoch - batch_size
        end = self._index_in_epoch
        self.output[self._perm[start:end], :] = output
        self.paths[self._perm[start:end]] = paths
        return

    @property
    def output(self):
        return self._output

    @property
    def paths(self):
        return self._paths

    @property
    def label(self):
        return self._dataset.get_labels()

    def finish_epoch(self):
        self._index_in_epoch = 0
        np.random.shuffle(self._perm)



class DHN(object):
    def __init__(self, config):

        tf.compat.v1.disable_eager_execution()

        np.set_printoptions(precision=4)
        # self.stage = tf.placeholder_with_default(tf.constant(0), [])  # tensorflow 1.x
        self.stage = tf.Variable(0, trainable=False, dtype=tf.int32)    # tensorflow 2.x
        self.device = config['device']
        self.output_dim = config['output_dim']
        self.n_class = config['label_dim']
        self.cq_lambda = config['cq_lambda']
        self.alpha = config['alpha']

        self.batch_size = config['batch_size']
        self.val_batch_size = config['val_batch_size']
        self.max_iter = config['max_iter']
        self.img_model = config['img_model']
        self.loss_type = config['loss_type']
        self.learning_rate = config['learning_rate']
        self.learning_rate_decay_factor = config['learning_rate_decay_factor']
        self.decay_step = config['decay_step']

        self.finetune_all = config['finetune_all']

        self.file_name = 'lr_{}_cqlambda_{}_alpha_{}_dataset_{}'.format(
            self.learning_rate,
            self.cq_lambda,
            self.alpha,
            config['dataset'])
        
        self.weights_file = config['weights_file']


        # Setup session
        # print("launching session")
        configProto = tf.compat.v1.ConfigProto() 
        configProto.gpu_options.allow_growth = True
        configProto.allow_soft_placement = True
        self.sess = tf.compat.v1.Session(config=configProto)

        # Create variables and placeholders

        with tf.device(self.device):
            self.img = tf.compat.v1.placeholder(tf.float32, [None, 256, 256, 3])
            self.img_label = tf.compat.v1.placeholder(tf.float32, [None, self.n_class])
            self.model_weights = config['model_weights']
            self.img_last_layer, self.deep_param_img, self.train_layers, self.train_last_layer = self.load_model()
            self.sess.run(tf.compat.v1.global_variables_initializer())
        return

    def load_model(self):
        if self.img_model == 'alexnet':
            img_output = img_alexnet_layers(
                self.img, self.batch_size, self.output_dim,
                self.stage, self.weights_file, val_batch_size=self.val_batch_size)
        else:
            raise Exception('cannot use such CNN model as ' + self.img_model)
        return img_output



    def compute_create_database(self, img_database):
        """
        Runs all images in img_database through the nn.
        returns the nn output without any change applied.

        @ img_database : Dataset object
        
        & return : output array with shape: ( samples, output_dim )
        """
        print(" -- %s #Database processing start" % (datetime.now()))
        database_batch = int(ceil(img_database.n_samples / self.val_batch_size))
        
        print(" -- %s #Processing %d samples in %d batches" % 
            (datetime.now(), img_database.n_samples, database_batch))

        for i in range(database_batch):
            images, labels, paths = img_database.next_batch(self.val_batch_size)

            # print(paths.dtype)

            output = self.sess.run([self.img_last_layer],
                                            feed_dict={self.img: images, self.img_label: labels, self.stage: 1})

            # print(output.type)

            img_database.feed_batch_output(self.val_batch_size, output, paths)

            # print(output)

            if i % 100 == 0:
                print('Completed %d out of %d batches.' % (i, database_batch))    

        print(" -- %s #Processing Complete." % (datetime.now()))
        
        self.sess.close()
        return img_database.output, img_database.paths # return database output


    def compute_pred_solo(self, pred_img):
        tmp_label = np.zeros((1,self.n_class))
        tmp_label[0][0] = 1
        output = self.sess.run([self.img_last_layer],
                                  feed_dict={self.img: pred_img, self.img_label: tmp_label,self.stage: 1})
        
        #self.sess.close()
        return output  
    
    #DHN end



def create_database(database_img, config):
    model = DHN(config)
    img_database = Dataset(database_img, config['output_dim'])
    output, path = model.compute_create_database(img_database)
    return output, path


def pred_solo(pred_img, config, model = 0):

    if(not model): model = DHN(config)
    output = model.compute_pred_solo(pred_img)
        
    return output, model

