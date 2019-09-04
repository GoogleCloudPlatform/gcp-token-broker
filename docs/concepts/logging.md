# Logging

## Enabling GCS audit logs

Follow these steps to enable GCS audit logs:

1. Go to: https://console.cloud.google.com/iam-admin/audit
2. In the "Filter table" text box, type "Google Cloud Storage" then press the "Enter" key.
3. Click on the "Google Cloud Storage" entry.
4. Tick the 3 checkboxes: "Admin Read", "Data Read", "Data Write".
5. Click "Save".

## Viewing GCS audit logs

Follow these steps to view the GCS audit logs in Stackdriver:

1. Open the logs viewer in Stackdriver: https://console.cloud.google.com/logs/viewer
2. Click the down arrow in the text search box, then click "Convert to advanced filter".
3. Type the following in the text search box (Replace the **two** **`[PROJECT-ID]`** instances
   with your project ID and **`[BUCKET-NAME]`** with the name of your bucket):

   ```conf
   resource.type="gcs_bucket"
   resource.labels.bucket_name="[BUCKET-NAME]"
   logName="projects/[PROJECT-ID]/logs/cloudaudit.googleapis.com%2Fdata_access"
   ```
4. Click "Submit Filter".