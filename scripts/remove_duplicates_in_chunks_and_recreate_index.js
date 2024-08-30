// usage: mongo "localhost" scripts/remove_duplicates_in_chunks_and_recreate_index.js

db = db.getSiblingDB('file-upload');

duplicates = db.getCollection('envelopes.chunks').aggregate([
  { $group: {
    _id: { fid: "$files_id", n: "$n" },
    count: { $sum: 1 }
  }},
  { $match: {
    count: { $gt: 1 }
  }}
]).toArray();

print("duplicates");
printjson(duplicates);

fileIds = duplicates.map(d => d._id.fid );

files = db.getCollection('envelopes.files').find({
  "_id" : {
     "$in" : fileIds
  }
}).toArray();

files;

print("files");
printjson(files);

envelopeIds = files.map(file => file.metadata.envelopeId );

db.getCollection('envelopes-read-model').remove({
  "_id" : {
    "$in" : envelopeIds
  }
});

db.getCollection('envelopes.files').remove({
  "_id" : {
    "$in" : fileIds
  }
});

db.getCollection('events').remove({
  "streamId" : {
    "$in" : envelopeIds
  }
});

db.getCollection('inprogress-files').remove({
  "envelopeId" : {
    "$in" : envelopeIds
  }
});

db.getCollection('envelopes.chunks').remove({
  "files_id" : {
    "$in" : fileIds
  }
});

db.envelopes.chunks.createIndex( { "files_id": 1, n: 1 }, { "unique" : true, "background" : true } )
