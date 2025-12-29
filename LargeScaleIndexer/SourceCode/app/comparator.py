import torch
import torch.nn as nn
import os 
import torch.nn.functional as F
from torchvision import transforms
from PIL import Image  
from io import BytesIO
import requests
import torchvision.transforms as transforms
from torchvision import transforms
from torch.utils.data import Dataset
from dotenv import load_dotenv
import os
import json
import base64


load_dotenv()

print("Face GPUs Available: " + str(torch.cuda.device_count()))


device = 'cuda:0' if torch.cuda.is_available() else 'cpu'
torch.cuda.manual_seed_all(1)

# device = 'cpu'
# Model parameters
length = 36
num = 6
words = 64
feature_dim = 516


class Block(nn.Module):
    def __init__(self, channels):
        super().__init__()
        self.conv1 = nn.Conv2d(channels, channels, kernel_size=3, stride=1, padding=1, bias=False)
        self.bn1 = nn.BatchNorm2d(channels)
        self.prelu1 = nn.PReLU(channels)
        self.conv2 = nn.Conv2d(channels, channels, kernel_size=3, stride=1, padding=1, bias=False)
        self.bn2 = nn.BatchNorm2d(channels)
        self.prelu2 = nn.PReLU(channels)

    def forward(self, x):
        short_cut = x
        x = self.conv1(x)
        x = self.bn1(x)
        x = self.prelu1(x)
        x = self.conv2(x)
        x = self.bn2(x)
        x = self.prelu2(x)

        return x + short_cut


class resnet20_pq(nn.Module):

    def __init__(self, num_layers=20, feature_dim=512, channel_max=512, size=7):    # size = 4 for 32*32 size dataset
        super().__init__()
        assert num_layers in [20, 64], 'spherenet num_layers should be 20 or 64'
        if num_layers == 20:
            layers = [1, 2, 4, 1]
        elif num_layers == 64:
            layers = [3, 8, 16, 3]
        else:
            raise ValueError('sphere' + str(num_layers) + "is not supported!")
        if channel_max == 512:
            filter_list = [3, 64, 128, 256, 512]
            if size == 7:
                stride_list = [2, 2, 2, 2]
            else:
                stride_list = [1, 2, 2, 2]

        else:
            filter_list = [3, 16, 32, 64, 128]
            stride_list = [1, 2, 2, 2]

        block = Block
        self.feature_dim = feature_dim
        self.layer1 = self._make_layer(block, filter_list[0], filter_list[1], layers[0], stride=stride_list[0])
        self.layer2 = self._make_layer(block, filter_list[1], filter_list[2], layers[1], stride=stride_list[1])
        self.layer3 = self._make_layer(block, filter_list[2], filter_list[3], layers[2], stride=stride_list[2])
        self.layer4 = self._make_layer(block, filter_list[3], filter_list[4], layers[3], stride=stride_list[3])
        self.bn = nn.BatchNorm1d(channel_max*size*size)
        self.fc = nn.Linear(channel_max*size*size, self.feature_dim)
        self.last_bn = nn.BatchNorm1d(self.feature_dim)
        self.drop = nn.Dropout()

    def _make_layer(self, block, inplanes, planes, num_units, stride):
        layers = []
        layers.append(nn.Conv2d(inplanes, planes, 3, stride, 1))
        layers.append(nn.BatchNorm2d(planes))
        layers.append(nn.PReLU(planes))
        for i in range(num_units):
            layers.append(block(planes))
        return nn.Sequential(*layers)

    def forward(self, x):
        x = self.layer1(x)
        x = self.layer2(x)
        x = self.layer3(x)
        x = self.layer4(x)
        x = x.view(x.size(0), -1)
        x = self.bn(x)
        x = self.drop(x)
        x = self.fc(x)
        out = self.last_bn(x)

        return out


# Get Image from base64
class ImageBase64Dataset(Dataset):
    def __init__(self, base64_string, transform=None, loader=None):
        self.base64_string = base64_string
        self.transform = transform
        self.loader = loader if loader else self.default_loader

    def default_loader(self, base64_string):
        # Decode base64 -> bytes -> PIL.Image
        image_data = base64.b64decode(base64_string)
        img = Image.open(BytesIO(image_data))
        return img.convert("RGB")  

    def __getitem__(self, index):
        img = self.loader(self.base64_string)
        if self.transform:
            img = self.transform(img)
        return img, self.base64_string  # return same structure as URL version

    def __len__(self):
        return 1




# Request image from URL
class ImageURLDataset(Dataset):
    def __init__(self, url, model_manager, transform=None, loader=None):
        self.url = url  
        self.transform = transform
        self.loader = loader if loader else self.default_loader
        self.model_manager = model_manager

    def default_loader(self, url):
        # Add the Authorization header
        cms_headers = {"Authorization": os.getenv("CMS_AUTH_HEADER")}

        response = self.model_manager.session_get(url, headers=cms_headers)
        # response = requests.get(url, headers=headers)
        if response.status_code == 200:
            img = Image.open(BytesIO(response.content))
            return img.convert("RGB")  
        else:
            raise Exception(f"Failed to fetch image from {url}. HTTP status code: {response.status_code}")

    def __getitem__(self, index):
        img = self.loader(self.url) 
        if self.transform:
            img = self.transform(img)
        return img, self.url
    
    def __len__(self):
        return 1



# Create the dataset and transformations
def get_datasets_transform(image_paths, model_manager, index):


    to_tensor = transforms.Compose([
        transforms.Resize((256, 256)),
        transforms.CenterCrop(224),
        # transforms.RandomCrop(224),
        # transforms.RandomHorizontalFlip(),
        transforms.ToTensor(),
    ])
    
    if (index):
        input_set = ImageBase64Dataset(image_paths, transform=to_tensor)
    else:    
        input_set = ImageURLDataset(image_paths, model_manager, transform=to_tensor)
    


    transform_input = torch.nn.Sequential(
        transforms.Resize(35),
        transforms.CenterCrop(32),
        # transforms.RandomCrop(32),
        # transforms.RandomHorizontalFlip(),
        transforms.ConvertImageDtype(torch.float),
        transforms.Normalize([0.639, 0.479, 0.404], [0.216, 0.183, 0.171])
    )
    
    return input_set, transform_input



def compute_quant_for_eval(transform, dataloader, net, device):
    bs = []

    with torch.no_grad():
        for i, (imgs, *_) in enumerate(dataloader):
            imgs = imgs.to(device)
            imgs = transform(imgs)
            hash_values = net(imgs)
            bs.append(hash_values.data)


    return torch.cat(bs)


def indexFace(image_paths, model_manager, weights, api_face, suspect_id, owner, sensitivity):

    weights = weights.to(device)

    index_set, transform_index = get_datasets_transform(image_paths, model_manager, True)

    index_loader = torch.utils.data.DataLoader(index_set, batch_size=256, shuffle=False, pin_memory=True, num_workers=4)

    net = resnet20_pq(num_layers=20, feature_dim=feature_dim, channel_max=512, size=4)
    net.to(device)

    current_dir = os.path.dirname(__file__)
    backbone_path = os.path.join(current_dir, "backbone.pt")

    new_state_dict = torch.load(backbone_path, map_location=device)
    # new_state_dict = torch.load("./backbone.pt")
    net.load_state_dict(new_state_dict)
    

    # mlp_weight = get_model_weights() 
    len_word = int(feature_dim / num)
    net.eval()

    # Create hash
    with torch.no_grad():


        index_features = compute_quant_for_eval(transform_index, index_loader, net, device)

        features_split = torch.split(index_features, len_word, dim=1)
        features_split = torch.stack(features_split)
    
        index = torch.argmax(F.softmax(torch.matmul(features_split, weights), dim=2), dim=2)

    print(index)

    # Encrypt the hash
    index = [item.item() for item in index]

    data = {"hash_indexes_face": index,
                "lea_id": "lea0"}

    encrypted_codes = requests.post(api_face+"encrypt_facial_hash_indexes", json=data).json()

    encrypted_codes = encrypted_codes['encrypted_hash_indexes_face']

    # Save them to pod
    
    new_line = f"{suspect_id},{encrypted_codes},{sensitivity}"

    try:
        existing_file = model_manager.get_catalogue('face_real')
        try:
            json_obj = json.loads(existing_file)
            if isinstance(json_obj, dict) and "error" in json_obj:
                print("[INFO] File not found on server. Creating new one.")
                existing_file = ""  # ignore the error string
        except json.JSONDecodeError:
            pass
    except Exception as e:
        print(f"[WARN] Could not fetch index file: {e}")
        existing_file = ""

    updated_file = existing_file.strip() + "\n" + new_line

    print("==> new index")
    # print(updated_file)

    file_path = "temp.txt"
    with open(file_path, "w") as f:
        f.write(updated_file)

    url = os.getenv("FACE_URL")

    last_split_index = url.rfind('%2F')
    post_url = url[:last_split_index + 3] 
    filename = url[last_split_index + 3:]   

    files = {
        "file": (filename, open(file_path, "rb"), "text/plain"),
    }
    data = {
        "fileName": filename,
        "contentType": "text/plain",
    }

    response = model_manager.session_post(post_url, files=files, data=data)


    return index


def calculateHashForSearchingFace(image_paths, model_manager, weights, api_face):

    weights = weights.to(device)


    query_set, transform_query = get_datasets_transform(image_paths, model_manager, False)

    query_loader = torch.utils.data.DataLoader(query_set, batch_size=256, shuffle=False, pin_memory=True, num_workers=4)

    net = resnet20_pq(num_layers=20, feature_dim=feature_dim, channel_max=512, size=4)
    net.to(device)

    current_dir = os.path.dirname(__file__)
    backbone_path = os.path.join(current_dir, "backbone.pt")

    new_state_dict = torch.load(backbone_path, map_location=device)
    # new_state_dict = torch.load("./backbone.pt")
    net.load_state_dict(new_state_dict)
    

    # mlp_weight = get_model_weights() 
    len_word = int(feature_dim / num)
    net.eval()


    with torch.no_grad():


        query_features = compute_quant_for_eval(transform_query, query_loader, net, device)

        features_split = torch.split(query_features, len_word, dim=1)
        features_split = torch.stack(features_split)
        norm_features = F.normalize(features_split, dim=2)

        # index
        index = torch.argmax(F.softmax(torch.matmul(features_split, weights), dim=2), dim=2)

        # search
        softmax_score = F.softmax(torch.matmul(norm_features[:, 0, :].unsqueeze(1), weights).squeeze(1), dim=1)
        query_features = (softmax_score * 10000).int()


        query_matrix = query_features.tolist()

        
        data = {"distances_matrix": query_matrix}

        resp = requests.post(api_face+"encrypt_facial_hash_representations/", json=data).json()

        encrypted_data = resp['encrypted_matrix']
        
        return encrypted_data, index




def PqDistRet_Ortho_for_eval(query_features, index_table, api_face, top=None):

    distances = []


    for j in range(len(index_table[0])):  
        current_array = [query_features[i][index_table[i][j]] for i in range(len(query_features))]

        data = {"encrypted_values": current_array}

        resp = requests.post(api_face + "calc_aggregated_distance/", json=data)
        resp = resp.json()
        encrypted_data = resp['aggregated_distance']

        distances.append(encrypted_data)


    distances = torch.tensor(distances).to(device)

    score, sort_result = torch.sort(distances, descending=True)

    top_results = sort_result[1:top]
    top_scores = score[1:top]

    top_scores = (top_scores.float() / score[0].item()) * 100
    top_scores[1:] -= 15

    return top_results, top_scores


   

def searchForMatchesFace(matrix, originator, lea, model_manager, similarity, api_encrypt_decrypt, api_face):

    file_content = model_manager.get_catalogue("face_real") + "\n" + model_manager.get_catalogue("face_synthetic")

    pq_data = []
    for line in file_content.splitlines():

        if line == '':
            continue 

        data = line.split(',')
        if len(data) < 3:
            continue
        # print(data)
        image_id = int(data[0])
        pq_code = data[1]
        is_sensitive = data[2].lower() == "true" 

        # If sensitive and originator != lea, skip this line
        if is_sensitive and originator != lea:
            continue 

        data = {"encrypted_hash_indexes_face": pq_code,
                "lea_id": "lea0"}
        decrypted_codes = requests.post(api_encrypt_decrypt+"decrypt_facial_hash_indexes", json=data).json()

        pq_data.append((image_id, decrypted_codes['hash_indexes_face']))

    folder_names = [entry[0] for entry in pq_data]  
    index = [entry[1] for entry in pq_data]

    index = torch.tensor(index).to(device)
    index = index.T 

    similarity = torch.tensor(similarity).to(device)

    index = torch.cat((index, similarity), dim=1)

    

    with torch.no_grad():

        top_results, scores = PqDistRet_Ortho_for_eval(matrix, index, api_face, top=11)

        top_folder_names = [folder_names[i-1] for i in top_results]

        return top_folder_names, scores 
